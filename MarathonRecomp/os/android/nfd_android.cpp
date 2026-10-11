// Android port: nativefiledialog-extended has no Android backend, and the
// installer only needs paths it can open with std::filesystem. The "picker"
// returns what the player copied into <root>/import:
//   - Add Files:  every file in import/ (the game ISO, DLC packages)
//   - Add Folder: every folder in import/, or import/ itself if it directly
//                 holds an extracted game (default.xex)

#include <nfd.h>
#include <os/android/android_paths.h>

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <string>
#include <vector>

namespace
{
    std::string g_error;

    struct PathSet
    {
        std::vector<std::string> paths;
    };

    std::filesystem::path ImportPath()
    {
        return std::filesystem::path(MARATHON_ANDROID_ROOT) / "import";
    }

    nfdresult_t Collect(const nfdpathset_t** outPaths, bool folders)
    {
        std::error_code ec;
        std::filesystem::path import = ImportPath();
        std::filesystem::create_directories(import, ec);

        auto* set = new PathSet();
        if (folders && std::filesystem::exists(import / "default.xex", ec))
        {
            set->paths.push_back(import.string());
        }
        else
        {
            for (const auto& entry : std::filesystem::directory_iterator(import, ec))
            {
                bool match = folders ? entry.is_directory(ec) : entry.is_regular_file(ec);
                if (match && entry.path().filename().string().rfind('.', 0) != 0)
                    set->paths.push_back(entry.path().string());
            }
        }

        std::sort(set->paths.begin(), set->paths.end());

        if (set->paths.empty())
        {
            delete set;
            g_error = std::string("Nothing found in ") + import.string() + (folders
                ? ". Copy the extracted game folder there, then try again."
                : ". Copy the game ISO (and any DLC files) there, then try again.");
            return NFD_ERROR;
        }

        *outPaths = set;
        return NFD_OKAY;
    }
}

extern "C" {

nfdresult_t NFD_Init(void) { return NFD_OKAY; }
void NFD_Quit(void) {}

const char* NFD_GetError(void) { return g_error.empty() ? nullptr : g_error.c_str(); }
void NFD_ClearError(void) { g_error.clear(); }

nfdresult_t NFD_OpenDialogMultipleN(const nfdpathset_t** outPaths, const nfdnfilteritem_t*, nfdfiltersize_t, const nfdnchar_t*)
{
    return Collect(outPaths, false);
}

nfdresult_t NFD_PickFolderMultipleN(const nfdpathset_t** outPaths, const nfdnchar_t*)
{
    return Collect(outPaths, true);
}

nfdresult_t NFD_PathSet_GetCount(const nfdpathset_t* pathSet, nfdpathsetsize_t* count)
{
    *count = nfdpathsetsize_t(static_cast<const PathSet*>(pathSet)->paths.size());
    return NFD_OKAY;
}

nfdresult_t NFD_PathSet_GetPathN(const nfdpathset_t* pathSet, nfdpathsetsize_t index, nfdnchar_t** outPath)
{
    const auto* set = static_cast<const PathSet*>(pathSet);
    if (index >= set->paths.size())
        return NFD_ERROR;

    *outPath = strdup(set->paths[index].c_str());
    return NFD_OKAY;
}

void NFD_PathSet_FreePathN(const nfdnchar_t* filePath) { free(const_cast<nfdnchar_t*>(filePath)); }
void NFD_PathSet_Free(const nfdpathset_t* pathSet) { delete static_cast<const PathSet*>(pathSet); }
void NFD_FreePathN(nfdnchar_t* filePath) { free(filePath); }

}
