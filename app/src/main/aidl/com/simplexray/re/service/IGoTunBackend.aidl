package com.simplexray.re.service;

import android.os.ParcelFileDescriptor;

interface IGoTunBackend {
    boolean start(in ParcelFileDescriptor tunFd, String socksHost, int socksPort, int mtu, String username, String password);
    boolean stop();
}
