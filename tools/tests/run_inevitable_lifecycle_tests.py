#!/usr/bin/env python3
"""Compile the real Inevitable stack and UI executor against headless test doubles.

These exercise ownership/cleanup and dispatch ordering without claiming to be
Minecraft, OpenGL, or real mouse-input integration tests.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUBS = {
    "org/jetbrains/annotations/NotNull.java": """package org.jetbrains.annotations; public @interface NotNull {}""",
    "gg/vape/Vape.java": """package gg.vape; public class Vape { public static final Vape INSTANCE=new Vape(); public final Settings settings=new Settings(); public Settings getClientSettings(){return settings;} public Object getSyncThread(){return null;} public void saveAndStop(){} public static void debugLog(String s){} public static class Settings { public double getGuiScaleFactor(){return 1;} } }""",
    "gg/vape/wrapper/impl/Minecraft.java": """package gg.vape.wrapper.impl; public class Minecraft { public static int J(){return 1600;} public static int h(){return 900;} }""",
    "gg/vape/module/none/ClientSettings.java": """package gg.vape.module.none; import gg.vape.ui.click.component.GuiComponent; import gg.vape.ui.click.frame.*; public class ClientSettings { public static GuiComponent activeComponent; public static FrameStackManager activeStack; public static void removePopup(PopupFrame popup){ if(activeStack!=null)activeStack.m(popup); popup.Q$src$Lgg_vape_ui_click_frame_Frame_$1y8ivjg().s$src$Ljava_util_ArrayList_$1a2240q().remove(popup); } }""",
    "gg/vape/ui/click/component/GuiComponent.java": """package gg.vape.ui.click.component; import java.util.*; import gg.vape.ui.click.frame.*; public class GuiComponent { private final List<GuiComponent> children=new ArrayList<GuiComponent>(); private FrameComponent parent; public List<GuiComponent> f(){return children;} public void add(GuiComponent child){children.add(child); child.parent=this instanceof FrameComponent?(FrameComponent)this:parent;} public FrameComponent getParentFrameComponent(){return parent;} public Frame L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa(){if(this instanceof Frame)return (Frame)this; return parent==null?null:parent.L$src$Lgg_vape_ui_click_frame_Frame_$1djx6sa();} }""",
    "gg/vape/ui/click/frame/FrameComponent.java": """package gg.vape.ui.click.frame; import gg.vape.ui.click.component.GuiComponent; public class FrameComponent extends GuiComponent {}""",
    "gg/vape/ui/click/frame/Frame.java": """package gg.vape.ui.click.frame; import java.util.*; public class Frame extends FrameComponent { private final ArrayList<PopupFrame> popups=new ArrayList<PopupFrame>(); private double x,y,max=300; public ArrayList<PopupFrame> s$src$Ljava_util_ArrayList_$1a2240q(){return popups;} public void W(double d){} public void M(double a,double b){x=a;y=b;} public double G$src$D$1b2f02a(){return x;} public double n(){return y;} public double d$src$D$ibccpu(){return max;} public void t(double d){max=d;} public void l$src$V$1mibm4x(){} }""",
    "gg/vape/ui/click/frame/PopupFrame.java": """package gg.vape.ui.click.frame; import gg.vape.ui.click.component.GuiComponent; public class PopupFrame extends Frame { private final GuiComponent source; private final Frame owner; public PopupFrame(GuiComponent source, Frame owner){this.source=source;this.owner=owner; owner.s$src$Ljava_util_ArrayList_$1a2240q().add(this);} public Frame Q$src$Lgg_vape_ui_click_frame_Frame_$1y8ivjg(){return owner;} public Frame X$src$Lgg_vape_ui_click_frame_Frame_$1aw5qf9(){Frame f=owner; while(f instanceof PopupFrame)f=((PopupFrame)f).owner; return f;} public GuiComponent V$src$Lgg_vape_ui_click_component_GuiComponent_$jpbobc(){return source;} }""",
    "gg/vape/ui/click/component/DropdownSelectComponent.java": """package gg.vape.ui.click.component; import gg.vape.ui.click.frame.PopupFrame; import gg.vape.module.none.ClientSettings; public class DropdownSelectComponent<T> extends GuiComponent { public PopupFrame popup; public PopupFrame getPopupFrame(){return popup;} public void closePopup(){PopupFrame p=popup;popup=null;if(ClientSettings.activeComponent==this)ClientSettings.activeComponent=null;ClientSettings.removePopup(p);} }""",
    "gg/vape/ui/click/component/PopupMenuButtonComponent.java": """package gg.vape.ui.click.component; import gg.vape.ui.click.frame.PopupFrame; import gg.vape.module.none.ClientSettings; public class PopupMenuButtonComponent extends GuiComponent { public PopupFrame popup; public PopupFrame getPopupFrame(){return popup;} public void closePopup(){PopupFrame p=popup;popup=null;if(ClientSettings.activeComponent==this)ClientSettings.activeComponent=null;ClientSettings.removePopup(p);} }""",
    "gg/vape/ui/click/component/module/ModuleComponent.java": """package gg.vape.ui.click.component.module; import java.util.*; import gg.vape.ui.click.component.GuiComponent; public class ModuleComponent extends GuiComponent { private final List<GuiComponent> values=new ArrayList<GuiComponent>(); public List<GuiComponent> getValueComponents(){return values;} }""",
    "gg/vape/ui/click/component/input/BindableInputComponent.java": """package gg.vape.ui.click.component.input; import java.util.*; import gg.vape.ui.click.component.GuiComponent; public class BindableInputComponent extends GuiComponent { public final Task task=new Task(); public Task getCaptureTask(){return task;} public static void cancelCapturesInTree(GuiComponent c){if(c instanceof BindableInputComponent)((BindableInputComponent)c).task.cancelCapture();for(GuiComponent child:new ArrayList<GuiComponent>(c.f()))cancelCapturesInTree(child);} public static class Task {public boolean capturing=true;public int cancellations;public void cancelCapture(){if(capturing){capturing=false;cancellations++;}}} }""",
    "gg/vape/ui/click/component/value/AbstractListValueComponent.java": """package gg.vape.ui.click.component.value; import gg.vape.ui.click.component.GuiComponent; public class AbstractListValueComponent extends GuiComponent { public boolean expanded; public void setExpanded(boolean b){expanded=b;} }""",
    "gg/vape/ui/click/component/value/FloatingValueDropdownLayer.java": """package gg.vape.ui.click.component.value; import gg.vape.ui.click.frame.Frame; public class FloatingValueDropdownLayer<T extends AbstractListValueComponent> extends Frame { private final T source; public FloatingValueDropdownLayer(T source){this.source=source;} public T getSourceComponent(){return source;} }""",
    "gg/vape/ui/click/frame/impl/inevitable/InevitableModuleFrame.java": """package gg.vape.ui.click.frame.impl.inevitable; import gg.vape.ui.click.frame.Frame; public class InevitableModuleFrame extends Frame { private boolean layout; public boolean hasLayout(){return layout;} public void clearLayout(){layout=false;} public void markLayoutAssigned(){layout=true;} public void syncModules(){} }""",
    "gg/vape/ui/click/frame/impl/inevitable/InevitablePanelFrame.java": """package gg.vape.ui.click.frame.impl.inevitable; import gg.vape.ui.click.frame.Frame; public class InevitablePanelFrame extends Frame { private boolean layout; public boolean hasLayout(){return layout;} public void clearLayout(){layout=false;} public void markLayoutAssigned(){layout=true;} }""",
}

TEST = r"""
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.component.*;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.component.input.BindableInputComponent;
import gg.vape.ui.click.component.value.*;
import gg.vape.ui.click.frame.*;
import gg.vape.ui.click.frame.impl.inevitable.*;
import gg.vape.utils.ThreadBoundExecutor;
import java.util.*;

public final class InevitableLifecycleTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        InevitableFrameStack stack=new InevitableFrameStack();
        InevitableModuleFrame first=new InevitableModuleFrame(), second=new InevitableModuleFrame();
        stack.addPanel(first); stack.addPanel(second);
        ClientSettings.activeStack=stack;
        ModuleComponent row=new ModuleComponent(); first.add(row);
        BindableInputComponent rowBind=new BindableInputComponent(); row.add(rowBind);
        GuiComponent editor=new GuiComponent(); first.add(editor); row.getValueComponents().add(editor);
        BindableInputComponent editorBind=new BindableInputComponent(); editor.add(editorBind);
        AbstractListValueComponent list=new AbstractListValueComponent(); editor.add(list); list.setExpanded(true);
        FloatingValueDropdownLayer<AbstractListValueComponent> sidecar=new FloatingValueDropdownLayer<>(list); stack.q(sidecar);
        GuiComponent focused=new GuiComponent(); sidecar.add(focused); ClientSettings.activeComponent=focused;
        DropdownSelectComponent<Object> inSidecar=new DropdownSelectComponent<>(); sidecar.add(inSidecar);
        PopupFrame sidePopup=new PopupFrame(inSidecar,sidecar); inSidecar.popup=sidePopup; stack.q(sidePopup);
        DropdownSelectComponent<Object> dropdown=new DropdownSelectComponent<>(); editor.add(dropdown);
        PopupFrame popup=new PopupFrame(dropdown,first); dropdown.popup=popup; stack.q(popup);
        PopupMenuButtonComponent nested=new PopupMenuButtonComponent(); popup.add(nested);
        PopupFrame nestedPopup=new PopupFrame(nested,popup); nested.popup=nestedPopup; stack.q(nestedPopup);
        DropdownSelectComponent<Object> untouched=new DropdownSelectComponent<>(); second.add(untouched);
        PopupFrame unrelated=new PopupFrame(untouched,second); untouched.popup=unrelated; stack.q(unrelated);
        check(InevitableFrameStack.isPanelOrPopup(sidecar),"sidecar belongs to panel");
        check(InevitableFrameStack.isPanelOrPopup(sidePopup),"popup on sidecar belongs to panel");
        check(InevitableFrameStack.isPanelOrPopup(nestedPopup),"nested popup belongs to panel");
        stack.closeEditorTree(editor);
        check(!editorBind.task.capturing && editorBind.task.cancellations==1 && rowBind.task.capturing,
                "collapsing an editor cancels only its own bind captures");
        check(!list.expanded,"collapsing tree collapses list");
        check(!stack.Y().contains(sidecar) && !stack.Y().contains(sidePopup),"sidecar and its popup removed");
        check(!stack.Y().contains(popup) && !stack.Y().contains(nestedPopup),"nested panel popups removed");
        check(dropdown.popup==null && nested.popup==null && inSidecar.popup==null,"control popup references reset");
        check(ClientSettings.activeComponent==null,"sidecar focus cleared");
        check(stack.Y().contains(unrelated) && untouched.popup==unrelated,"other panel left intact");
        check(first.s$src$Ljava_util_ArrayList_$1a2240q().isEmpty(),"owner popup list cleared");
        stack.closeTransients();
        check(!rowBind.task.capturing && rowBind.task.cancellations==1,
                "navigation cancels module-row binds as well as editor binds");
        check(!stack.Y().contains(unrelated) && untouched.popup==null,"navigation closes remaining popup");
        stack.A();
        check(first.hasLayout() && second.hasLayout(),"first render assigns positions");
        stack.resetLayout();
        check(!first.hasLayout() && !second.hasLayout(),"layout reset");

        ThreadBoundExecutor executor=new ThreadBoundExecutor();
        List<String> events=new ArrayList<>();
        executor.runPending(); // establish owner
        executor.executeLater(()->events.add("later"));
        check(events.isEmpty(),"executeLater defers even on owner thread");
        executor.execute(()->events.add("immediate"));
        check(events.equals(Arrays.asList("immediate")),"ordinary execute remains immediate");
        Thread worker=new Thread(()->executor.execute(()->events.add("worker")));
        worker.start(); worker.join();
        check(events.size()==1,"worker task remains queued");
        executor.runPending();
        check(events.equals(Arrays.asList("immediate","later","worker")),"drain preserves order");
        System.out.println("Inevitable popup ownership and UI dispatch checks passed (headless doubles)");
    }
}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="inevitable-test-") as tmp:
        tmp = Path(tmp)
        sources = []
        for name, content in STUBS.items():
            path = tmp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(str(path))
        test = tmp / "InevitableLifecycleTest.java"
        test.write_text(TEST, encoding="utf-8")
        sources += [str(test),
                    str(ROOT / "src/main/java/gg/vape/ui/click/frame/FrameStackManager.java"),
                    str(ROOT / "src/main/java/gg/vape/ui/click/frame/impl/inevitable/InevitableFrameStack.java"),
                    str(ROOT / "src/main/java/gg/vape/ui/click/frame/impl/inevitable/InevitableLayout.java"),
                    str(ROOT / "src/main/java/gg/vape/utils/ThreadBoundExecutor.java")]
        classes = tmp / "classes"
        classes.mkdir()
        subprocess.run([args.javac, "--release", "8", "-encoding", "UTF-8", "-d", str(classes)] + sources,
                       cwd=ROOT, check=True)
        subprocess.run([args.java, "-cp", str(classes), "InevitableLifecycleTest"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
