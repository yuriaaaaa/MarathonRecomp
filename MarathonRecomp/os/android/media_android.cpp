#include <os/media.h>

bool os::media::IsExternalMediaPlaying()
{
    // Android handles audio focus itself.
    return false;
}
