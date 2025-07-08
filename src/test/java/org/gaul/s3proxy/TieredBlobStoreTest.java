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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.google.common.io.ByteSource;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class TieredBlobStoreTest {
    private static final ByteSource BYTE_SOURCE =
            TestUtils.randomByteSource().slice(0, 1024);

    private BlobStoreContext hotContext;
    private BlobStoreContext coldContext;
    private BlobStore hotStore;
    private BlobStore coldStore;
    private ScheduledExecutorService executor;
    private BlobStore tieredStore;
    private String containerName;

    @Before
    public void setUp() {
        containerName = TestUtils.createRandomContainerName();

        hotContext = ContextBuilder
                .newBuilder("transient")
                .credentials("id", "cred")
                .modules(List.of(new SLF4JLoggingModule()))
                .build(BlobStoreContext.class);
        hotStore = hotContext.getBlobStore();
        hotStore.createContainerInLocation(null, containerName);

        coldContext = ContextBuilder
                .newBuilder("transient")
                .credentials("id", "cred")
                .modules(List.of(new SLF4JLoggingModule()))
                .build(BlobStoreContext.class);
        coldStore = coldContext.getBlobStore();
        coldStore.createContainerInLocation(null, containerName);

        executor = Executors.newScheduledThreadPool(1);
        tieredStore = TieredBlobStore.newTieredBlobStore(
                hotStore, coldStore, executor, 0);
    }

    @After
    public void tearDown() {
        if (hotContext != null) {
            hotStore.deleteContainer(containerName);
            hotContext.close();
        }
        if (coldContext != null) {
            coldStore.deleteContainer(containerName);
            coldContext.close();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    public void testOldBlobMigratesToCold() throws Exception {
        String blobName = TestUtils.createRandomBlobName();
        Blob blob = makeBlob(tieredStore, blobName);
        tieredStore.putBlob(containerName, blob);

        ((TieredBlobStore) tieredStore).scanForOldBlobs();

        assertThat(hotStore.getBlob(containerName, blobName)).isNull();
        assertThat(coldStore.getBlob(containerName, blobName)).isNotNull();
        assertThat(tieredStore.getBlob(containerName, blobName)).isNotNull();
    }

    private static Blob makeBlob(BlobStore store, String name) throws IOException {
        return store.blobBuilder(name)
                .payload(BYTE_SOURCE)
                .contentLength(BYTE_SOURCE.size())
                .build();
    }
}

