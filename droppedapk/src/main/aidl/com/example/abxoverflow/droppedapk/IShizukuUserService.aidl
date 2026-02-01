// IShizukuUserService.aidl
package com.example.abxoverflow.droppedapk;

import com.example.abxoverflow.droppedapk.shizuku.ShellResult;

interface IShizukuUserService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    boolean canUseRunAs() = 1;

    ShellResult run(String command) = 2;
}