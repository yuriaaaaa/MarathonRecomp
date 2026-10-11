#include "update_checker.h"

// Android port: no libcurl; update checks are disabled.

void UpdateChecker::initialize()
{
}

bool UpdateChecker::start()
{
    return false;
}

UpdateChecker::Result UpdateChecker::check()
{
    return Result::AlreadyUpToDate;
}

void UpdateChecker::visitWebsite()
{
}
