# SMB BlobStore Implementation Plan

This document outlines the steps required to implement a new jclouds provider that accesses an SMB/CIFS share directly.

## Goal
Provide a `smb` provider for S3Proxy so that objects are stored on a remote SMB server without mounting the share on the local filesystem.

## Strategy
1. **Implement a new BlobStore**
   - Create `SmbBlobStore` implementing jclouds `BlobStore` APIs.
   - Reuse the structure of `FilesystemNio2BlobStore` for guidance but replace NIO.2 calls with operations via an SMB client library.
   - Manage connections through a library such as [smbj](https://github.com/hierynomus/smbj) which supports SMB2/SMB3.
2. **Provider metadata and context module**
   - Create `SmbBlobApiMetadata`, `SmbBlobProviderMetadata`, and `SmbBlobStoreContextModule` similar to the NIO.2 implementations.
   - Register the provider with jclouds using `AutoService`.
   - Expose configuration properties for hostname, share name, domain, username, and password.
3. **Integrate with S3Proxy**
   - Add `smb` as a valid `jclouds.provider` value in configuration parsing.
   - Update `Quirks` with any compatibility flags (e.g., multipart stubs or lack of ACL support).
4. **Testing**
   - Add unit tests and integration tests using an in-memory SMB server (e.g., [mock-smb-server](https://github.com/hierynomus/smbj-rpc/tree/master/mssmbj)) or a Docker container running Samba.
   - Ensure the S3Proxy test suite runs with the new provider.
5. **Documentation**
   - Document new configuration options and example `s3proxy.conf` in `README.md` and/or the wiki.
   - Mention any limitations compared to local filesystem providers.

Following this plan will allow S3Proxy to store data directly on remote SMB shares through jclouds.
