#include "loader_language.h"

#include <cstdio>
#include <string>

namespace {
int failures = 0;

void expect(const char* label, const std::string& json, bool english) {
    const bool actual = loaderLanguageIsEnglish(json);
    if (actual != english) {
        std::printf("FAIL %s: got %s, expected %s\n", label,
                actual ? "English" : "Chinese", english ? "English" : "Chinese");
        ++failures;
    } else {
        std::printf("ok   %s\n", label);
    }
}
} // namespace

int main() {
    expect("missing config", "", true);
    expect("empty object", "{}", true);
    expect("missing language", R"({"settings":{"otherData":[{"id":"GUI Bind","value":null}]}})", true);
    expect("explicit English", R"({"settings":{"otherData":[{"id":"Language","value":"English"}]}})", true);
    expect("explicit Chinese", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})", false);
    expect("case insensitive", R"({"settings":{"otherData":[{"id":"LANGUAGE","value":"cHiNeSe"}]}})", false);
    expect("member order", R"({"settings":{"otherData":[{"value":"Chinese","id":"Language"}]}})", false);
    expect("escaped language names", R"({"settings":{"otherData":[{"id":"Lang\u0075age","value":"Chin\u0065se"}]}})", false);
    expect("UTF-8 BOM and whitespace", "\xef\xbb\xbf \n" R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})" " \n", false);
    expect("legacy Spanish", R"({"settings":{"otherData":[{"id":"Language","value":"Spanish"}]}})", true);
    expect("unknown language", R"({"settings":{"otherData":[{"id":"Language","value":"Klingon"}]}})", true);
    expect("null language", R"({"settings":{"otherData":[{"id":"Language","value":null}]}})", true);
    expect("array instead of language string", R"({"settings":{"otherData":[{"id":"Language","value":["Chinese"]}]}})", true);
    expect("not settings.otherData", R"({"profiles":{"id":"Language","value":"Chinese"},"settings":{"otherData":[]}})", true);
    expect("not the id member", R"({"settings":{"otherData":[{"id":"Other","value":"Language","label":"Chinese"}]}})", true);
    expect("nested lookalike", R"({"settings":{"otherData":[{"nested":{"id":"Language","value":"Chinese"}}]}})", true);
    expect("braces and quotes inside text", R"({"notes":"\" } { [ ]","settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})", false);
    expect("last recognized entry wins", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"},{"id":"Language","value":"English"}]}})", true);
    expect("unknown entry is ignored by product", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"},{"id":"Language","value":"unknown"}]}})", false);
    expect("duplicate settings key follows Gson", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}]},"settings":{}})", true);
    expect("duplicate otherData key follows Gson", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}],"otherData":[]}})", true);
    expect("duplicate value key follows Gson", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese","value":null}]}})", true);
    expect("invalid document", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}]})", true);
    expect("trailing garbage", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}} trailing)", true);
    expect("invalid escape", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese\q"}]}})", true);
    expect("invalid Unicode escape", R"({"settings":{"otherData":[{"id":"Language","value":"Chin\u00z0se"}]}})", true);
    expect("trailing comma", R"({"settings":{"otherData":[{"id":"Language","value":"Chinese"},]}})", true);
    expect("invalid number", R"({"other":01,"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})", true);
    expect("valid unrelated data", R"({"x":[-12.50e+3,true,false,null,{},[]],"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})", false);
    std::string deeplyNested = "{\"x\":" + std::string(140, '[') + "0" + std::string(140, ']')
            + R"(,"settings":{"otherData":[{"id":"Language","value":"Chinese"}]}})";
    expect("excessive nesting falls back safely", deeplyNested, true);

    std::printf("\n%s (%d failure(s))\n", failures == 0 ? "PASS" : "FAIL", failures);
    return failures == 0 ? 0 : 1;
}
