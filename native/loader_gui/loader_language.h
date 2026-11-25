#pragma once

#include <string>

// The product omits default-valued settings from config.json. English is the
// default; an explicitly saved Chinese selection is the only case in which the
// loader uses its Chinese table. Invalid documents and missing recognized
// settings use English; unsupported entries cannot override a valid selection.
// This parser is independent of Win32 so it can be tested on other platforms.
bool loaderLanguageIsEnglish(const std::string& json);
