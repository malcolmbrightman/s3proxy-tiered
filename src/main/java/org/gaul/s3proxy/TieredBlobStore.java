/*
 * Copyright 2014-2025 Andrew Gaul <andrew@gaul.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gaul.s3proxy;

import static java.util.Objects.requireNonNull;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.GetOptions;
import org.jclouds.blobstore.options.PutOptions;
import org.jclouds.blobstore.util.ForwardingBlobStore;
import org.jclouds.domain.Location;

/**
 * A BlobStore wrapper that keeps recently accessed objects on a hot backend
 * and migrates older ones to a cold backend. Reads first consult the hot
 * backend before falling back to the cold backend.
 */
final class TieredBlobStore extends ForwardingBlobStore {
    private final BlobStore hot;
    private final BlobStore cold;
    private final ScheduledExecutorService executor;
    private final long ageMillis;

    private TieredBlobStore(BlobStore hot, BlobStore cold,
            ScheduledExecutorService executor, int ageDays) {
        super(hot);
        this.hot = requireNonNull(hot);
        this.cold = requireNonNull(cold);
        this.executor = requireNonNull(executor);
        this.ageMillis = TimeUnit.DAYS.toMillis(ageDays);

        // run every hour
        this.executor.scheduleWithFixedDelay(this::scanForOldBlobs,
                1, 1, TimeUnit.HOURS);
    }

    static BlobStore newTieredBlobStore(BlobStore hot, BlobStore cold,
            ScheduledExecutorService executor, int ageDays) {
        return new TieredBlobStore(hot, cold, executor, ageDays);
    }

    @Override
    public Blob getBlob(String containerName, String name) {
        return getBlob(containerName, name, GetOptions.NONE);
    }

    @Override
    public Blob getBlob(String containerName, String name,
            GetOptions options) {
        Blob blob = hot.getBlob(containerName, name, options);
        if (blob == null) {
            blob = cold.getBlob(containerName, name, options);
        }
        return blob;
    }

    @Override
    public BlobMetadata blobMetadata(String container, String name) {
        BlobMetadata meta = hot.blobMetadata(container, name);
        if (meta == null) {
            meta = cold.blobMetadata(container, name);
        }
        return meta;
    }

    /** Move blobs older than the threshold from hot to cold storage. */
    void scanForOldBlobs() {
        long cutoff = System.currentTimeMillis() - ageMillis;
        PageSet<? extends StorageMetadata> containers = hot.list();
        for (StorageMetadata containerMeta : containers) {
            String container = containerMeta.getName();
            if (!cold.containerExists(container)) {
                cold.createContainerInLocation((Location) null, container);
            }

            PageSet<? extends StorageMetadata> blobs = hot.list(container);
            for (StorageMetadata sm : blobs) {
                Date lastModified = sm.getLastModified();
                if (lastModified != null &&
                        lastModified.getTime() < cutoff) {
                    Blob blob = hot.getBlob(container, sm.getName());
                    cold.putBlob(container, blob, PutOptions.NONE);
                    hot.removeBlob(container, sm.getName());
                }
            }
        }
    }
}

