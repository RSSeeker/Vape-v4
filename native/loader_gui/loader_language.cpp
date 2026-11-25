#include "loader_language.h"

#include <cstddef>
#include <string>

namespace {

bool equalsIgnoreCase(const std::string& value, const char* expected) {
    std::size_t i = 0;
    for (; i < value.size() && expected[i] != '\0'; ++i) {
        char a = value[i];
        char b = expected[i];
        if (a >= 'A' && a <= 'Z') a = static_cast<char>(a - 'A' + 'a');
        if (b >= 'A' && b <= 'Z') b = static_cast<char>(b - 'A' + 'a');
        if (a != b) return false;
    }
    return i == value.size() && expected[i] == '\0';
}

// A bounded, streaming JSON reader: only settings.otherData is interpreted;
// other values are validated and skipped without building a second copy of a
// potentially large profiles/config file. Keys and the two language names of
// interest are ASCII; non-ASCII escapes cannot match any of them.
class ConfigReader {
public:
    explicit ConfigReader(const std::string& input) : input_(input) {}

    bool read(bool& english) {
        if (input_.size() >= 3 &&
                static_cast<unsigned char>(input_[0]) == 0xef &&
                static_cast<unsigned char>(input_[1]) == 0xbb &&
                static_cast<unsigned char>(input_[2]) == 0xbf) {
            position_ = 3; // Gson also permits an optional UTF-8 BOM.
        }
        bool selected = true;
        if (!object(0, [&](const std::string& key, std::size_t depth) {
            if (key != "settings") return skipValue(depth);
            bool candidate = true; // last duplicate object key wins, as in Gson
            const bool ok = peek() == '{' ? settings(depth, candidate) : skipValue(depth);
            selected = candidate;
            return ok;
        })) return false;
        whitespace();
        if (position_ != input_.size()) return false;
        english = selected;
        return true;
    }

private:
    static const std::size_t kMaxDepth = 128;
    const std::string& input_;
    std::size_t position_ = 0;

    void whitespace() {
        while (position_ < input_.size()) {
            const char c = input_[position_];
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
            ++position_;
        }
    }

    char peek() {
        whitespace();
        return position_ < input_.size() ? input_[position_] : '\0';
    }

    bool take(char expected) {
        if (peek() != expected) return false;
        ++position_;
        return true;
    }

    static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    bool string(std::string* output) {
        if (!take('"')) return false;
        if (output != nullptr) output->clear();
        while (position_ < input_.size()) {
            unsigned char c = static_cast<unsigned char>(input_[position_++]);
            if (c == '"') return true;
            if (c < 0x20) return false;
            if (c == '\\') {
                if (position_ >= input_.size()) return false;
                const char escaped = input_[position_++];
                switch (escaped) {
                    case '"': c = '"'; break;
                    case '\\': c = '\\'; break;
                    case '/': c = '/'; break;
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'u': {
                        if (input_.size() - position_ < 4) return false;
                        unsigned value = 0;
                        for (int i = 0; i < 4; ++i) {
                            const int digit = hexDigit(input_[position_++]);
                            if (digit < 0) return false;
                            value = value * 16 + static_cast<unsigned>(digit);
                        }
                        c = value <= 0x7f ? static_cast<unsigned char>(value) : 0x80;
                        break;
                    }
                    default: return false;
                }
            }
            if (output != nullptr) output->push_back(static_cast<char>(c));
        }
        return false;
    }

    template <typename Member>
    bool object(std::size_t depth, Member member) {
        if (depth >= kMaxDepth || !take('{')) return false;
        if (take('}')) return true;
        for (;;) {
            std::string key;
            if (!string(&key) || !take(':') || !member(key, depth + 1)) return false;
            if (take('}')) return true;
            if (!take(',')) return false;
        }
    }

    template <typename Item>
    bool array(std::size_t depth, Item item) {
        if (depth >= kMaxDepth || !take('[')) return false;
        if (take(']')) return true;
        for (;;) {
            if (!item(depth + 1)) return false;
            if (take(']')) return true;
            if (!take(',')) return false;
        }
    }

    bool literal(const char* expected) {
        for (; *expected != '\0'; ++expected) {
            if (position_ >= input_.size() || input_[position_++] != *expected) return false;
        }
        return true;
    }

    bool number() {
        if (position_ < input_.size() && input_[position_] == '-') ++position_;
        if (position_ >= input_.size()) return false;
        if (input_[position_] == '0') {
            ++position_;
        } else {
            if (input_[position_] < '1' || input_[position_] > '9') return false;
            while (position_ < input_.size() && input_[position_] >= '0'
                    && input_[position_] <= '9') ++position_;
        }
        if (position_ < input_.size() && input_[position_] == '.') {
            ++position_;
            const std::size_t start = position_;
            while (position_ < input_.size() && input_[position_] >= '0'
                    && input_[position_] <= '9') ++position_;
            if (position_ == start) return false;
        }
        if (position_ < input_.size() && (input_[position_] == 'e' || input_[position_] == 'E')) {
            ++position_;
            if (position_ < input_.size() && (input_[position_] == '+' || input_[position_] == '-')) ++position_;
            const std::size_t start = position_;
            while (position_ < input_.size() && input_[position_] >= '0'
                    && input_[position_] <= '9') ++position_;
            if (position_ == start) return false;
        }
        return true;
    }

    bool skipValue(std::size_t depth) {
        switch (peek()) {
            case '{': return object(depth, [&](const std::string&, std::size_t child) {
                return skipValue(child);
            });
            case '[': return array(depth, [&](std::size_t child) { return skipValue(child); });
            case '"': return string(nullptr);
            case 't': return literal("true");
            case 'f': return literal("false");
            case 'n': return literal("null");
            default: return number();
        }
    }

    bool settings(std::size_t depth, bool& english) {
        return object(depth, [&](const std::string& key, std::size_t child) {
            if (key != "otherData") return skipValue(child);
            bool candidate = true;
            const bool ok = peek() == '[' ? otherData(child, candidate) : skipValue(child);
            english = candidate;
            return ok;
        });
    }

    bool otherData(std::size_t depth, bool& english) {
        return array(depth, [&](std::size_t child) {
            return peek() == '{' ? entry(child, english) : skipValue(child);
        });
    }

    bool entry(std::size_t depth, bool& english) {
        std::string id;
        std::string value;
        bool idIsString = false;
        bool valueIsString = false;
        const bool ok = object(depth, [&](const std::string& key, std::size_t child) {
            if (key == "id") {
                idIsString = peek() == '"';
                return idIsString ? string(&id) : skipValue(child);
            }
            if (key == "value") {
                valueIsString = peek() == '"';
                return valueIsString ? string(&value) : skipValue(child);
            }
            return skipValue(child);
        });
        if (ok && idIsString && valueIsString && equalsIgnoreCase(id, "Language")) {
            if (equalsIgnoreCase(value, "Chinese")) english = false;
            else if (equalsIgnoreCase(value, "English")) english = true;
            // Unsupported values are ignored by the product, rather than
            // overriding a preceding valid language in the same array.
        }
        return ok;
    }
};

} // namespace

bool loaderLanguageIsEnglish(const std::string& json) {
    bool english = true;
    ConfigReader reader(json);
    return !reader.read(english) || english;
}
