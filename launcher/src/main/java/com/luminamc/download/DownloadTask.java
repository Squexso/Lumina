package com.luminamc.download;

import java.nio.file.Path;

/** One file to fetch: source URL, destination, optional SHA-1 and known size. */
public final class DownloadTask {

    public final String url;
    public final Path   dest;
    public final String sha1;   // nullable
    public final long   size;   // bytes, 0 if unknown
    public final String label;

    public DownloadTask(String url, Path dest, String sha1, long size, String label) {
        this.url = url;
        this.dest = dest;
        this.sha1 = sha1;
        this.size = size;
        this.label = label != null ? label : dest.getFileName().toString();
    }

    public DownloadTask(String url, Path dest, String sha1, long size) {
        this(url, dest, sha1, size, null);
    }
}
