#!/usr/bin/env python3
"""Exercise actual public-profile transition and draft-reservation callbacks.

The view builder, request, popup and profile are doubles. These checks cover
ordering and late responses, not Minecraft rendering or a real online service.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FRAME = ROOT / "src/main/java/gg/vape/ui/click/frame/impl/profile/PublicProfilesFrame.java"


def extract_method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end]


TEST = r"""
import java.util.*;
public class PublicProfileConnectionTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    enum OnlineConnectionState { ONLINE, OFFLINE }
    static class OnlineConnectionManager {
        static final OnlineConnectionManager INSTANCE = new OnlineConnectionManager();
        OnlineConnectionState state = OnlineConnectionState.OFFLINE;
        OnlineConnectionState getConnectionState() { return state; }
    }
    static class PopupFrame {
        boolean V$src$Z$1xhop3l() { return false; }
        void T$src$V$1wse0de() { throw new AssertionError("hidden popup updated"); }
    }
    static class Profile {
        boolean draft = true;
        UUID id;
        boolean isDraft() { return draft; }
        void setOnlineId(UUID id) { this.id = id; }
    }
    static class ApiResponse {
        boolean successful; Object data;
        ApiResponse(boolean successful, Object data) { this.successful = successful; this.data = data; }
        boolean isSuccessful() { return successful; }
        Object getData() { return data; }
    }
    static class DraftPage {
        Profile draftProfile;
/* ACTUAL_PAGE_RESERVATION */
    }
    static class ClassicPanel {
/* ACTUAL_CLASSIC_RESERVATION */
    }
    static class BaseFrame { public void u() {} }
    static class Frame extends BaseFrame {
        boolean showingConnectionStatus = true, connectionRefreshPending = true;
        PopupFrame activePopup;
        List<String> events = new ArrayList<String>();
        void W() { showingConnectionStatus = false; events.add("rebuild"); }
        void d$src$V$fo8605() { showingConnectionStatus = true; events.add("offline"); }
        void refreshOwnedProfiles() { events.add("refresh"); }
/* ACTUAL_METHOD */
    }
    public static void main(String[] args) {
        Frame frame = new Frame();
        frame.u(); frame.u();
        check(frame.events.isEmpty(), "offline ticks must not repeatedly rebuild the status view");
        OnlineConnectionManager.INSTANCE.state = OnlineConnectionState.ONLINE;
        frame.u();
        check(!frame.showingConnectionStatus && !frame.connectionRefreshPending,
                "reconnect must leave the status view immediately");
        check(frame.events.equals(Arrays.asList("rebuild", "refresh")),
                "the online view must rebuild before the asynchronous refresh");
        frame.u(); frame.u();
        check(frame.events.size() == 2, "a failed or unchanged refresh must not cause repeated requests");
        OnlineConnectionManager.INSTANCE.state = OnlineConnectionState.OFFLINE;
        frame.u(); frame.u();
        check(frame.showingConnectionStatus && frame.events.size() == 3 && frame.events.get(2).equals("offline"),
                "a later disconnect returns to the status view once");
        OnlineConnectionManager.INSTANCE.state = OnlineConnectionState.ONLINE;
        frame.u(); frame.u();
        check(frame.events.equals(Arrays.asList("rebuild", "refresh", "offline", "rebuild", "refresh")),
                "a second reconnect rebuilds and refreshes once");

        UUID reserved = UUID.randomUUID(), saved = UUID.randomUUID();
        ApiResponse success = new ApiResponse(true, reserved);
        DraftPage page = new DraftPage();
        Profile first = new Profile(), replacement = new Profile();
        page.draftProfile = first;
        page.handleDraftProfileIdResponse(first, success, null);
        check(reserved.equals(first.id), "a live draft accepts its reservation");
        first.draft = false; first.id = saved;
        page.handleDraftProfileIdResponse(first, success, null);
        check(saved.equals(first.id), "a response after submission cannot overwrite a save ID");
        first.draft = true; page.draftProfile = replacement;
        page.handleDraftProfileIdResponse(first, success, null);
        check(saved.equals(first.id) && replacement.id == null, "an old request cannot affect a replacement draft");
        page.draftProfile = null;
        page.handleDraftProfileIdResponse(first, success, null);
        check(saved.equals(first.id), "a cancelled draft ignores the late response");
        page.draftProfile = replacement;
        page.handleDraftProfileIdResponse(replacement, new ApiResponse(false, reserved), null);
        page.handleDraftProfileIdResponse(replacement, success, new RuntimeException("offline"));
        check(replacement.id == null, "failed draft reservations leave the ID unset");
        ClassicPanel.handleProfileIdResponse(replacement, success, null);
        check(reserved.equals(replacement.id), "the classic live draft accepts a reservation");
        replacement.draft = false; replacement.id = saved;
        ClassicPanel.handleProfileIdResponse(replacement, success, null);
        check(saved.equals(replacement.id), "the classic draft ignores a response after submission");
        System.out.println("Public profile connection and draft reservation callbacks passed (actual methods, headless doubles)");
    }
}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    source = FRAME.read_text(encoding="utf-8")
    method = extract_method(source, "    @Override\n    public void u() {")
    page = (ROOT / "src/main/java/gg/vape/ui/click/frame/impl/main/ClickGuiProfilesPage.java").read_text(encoding="utf-8")
    panel = (ROOT / "src/main/java/gg/vape/ui/click/frame/impl/profile/ProfileCreatePanelComponent.java").read_text(encoding="utf-8")
    page_callback = extract_method(page, "    private void handleDraftProfileIdResponse(")
    panel_callback = extract_method(panel, "    private static void handleProfileIdResponse(")
    # The doubles model the state changes performed by the actual builders.
    assert "this.showingConnectionStatus = false;" in extract_method(source, "    private void W() {")
    assert "this.showingConnectionStatus = true;" in extract_method(source, "    private void d$src$V$fo8605() {")
    assert "this.draftProfile = null;" in extract_method(page, "    private void cancelCreateProfile() {")
    assert "this.draftProfile.setDraft(false);" in extract_method(page, "    private void createProfile() {")
    with tempfile.TemporaryDirectory(prefix="public-profile-connection-") as directory:
        tmp = Path(directory)
        test = tmp / "PublicProfileConnectionTest.java"
        test.write_text(TEST.replace("/* ACTUAL_METHOD */", method)
                        .replace("/* ACTUAL_PAGE_RESERVATION */", page_callback)
                        .replace("/* ACTUAL_CLASSIC_RESERVATION */", panel_callback), encoding="utf-8")
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(tmp), str(test)], cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(tmp), "PublicProfileConnectionTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
