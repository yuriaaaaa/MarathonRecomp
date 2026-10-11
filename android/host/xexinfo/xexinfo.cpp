// Host tool: loads default.xex like XenonRecomp, prints its sections and writes
// the mapped image (base..base+size) to a raw file for inspection.
#include <image.h>
#include <file.h>
#include <cstdio>
#include <fstream>

int main(int argc, char** argv)
{
    if (argc < 3) { fprintf(stderr, "usage: xexinfo default.xex image.bin\n"); return 1; }
    auto file = LoadFile(argv[1]);
    if (file.empty()) { fprintf(stderr, "can't read %s\n", argv[1]); return 1; }
    Image image = Image::ParseImage(file.data(), file.size());
    printf("base %zx size %x entry %zx\n", image.base, image.size, image.entry_point);
    for (auto& s : image.sections)
        printf("section %-8s %08zx-%08zx flags %d\n", s.name.c_str(), s.base, s.base + s.size, int(s.flags));
    std::ofstream out(argv[2], std::ios::binary);
    out.write(reinterpret_cast<const char*>(image.data.get()), image.size);
    return 0;
}
