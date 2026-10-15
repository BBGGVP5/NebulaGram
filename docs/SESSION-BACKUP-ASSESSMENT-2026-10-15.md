# Local session-backup assessment

This is an implementation assessment, not an available NebulaGram backup feature. Ordinary settings transfer continues to contain presentation preferences only.

## Verified storage boundaries

- Android's pinned `ConnectionsManager::saveConfig` writes account-specific `tgnet.dat`; `Datacenter::serializeToStream` includes permanent/temporary authorization material. These files are not presentation settings. Source: `TMessagesProj/jni/tgnet/ConnectionsManager.cpp`, `Datacenter.cpp`, official pin `f32ffeec763f0bdbf4bec25b52d654f77bff733d`.
- iOS's `makeExclusiveKeychain` is an MTProto adapter to `Postbox.keychainEntryForKey`/`setKeychainEntryForKey`, with an account-record generation check. Its name does not mean that copying it produces an Apple Keychain backup. The account opens with `ValueBoxEncryptionParameters` supplied separately.
- The pinned iOS core already defines `AccountBackupData` with master/additional datacenter keys and notification encryption material, and `accountWithId(... backupData:)` accepts it. It is authorization data, not a harmless Codable preference. Source: `submodules/TelegramCore/Sources/Account/Account.swift`, `SyncCore/SyncCore_AccountBackupDataAttribute.swift`, official pin `f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838`.
- Android's `NebulaSettingsTransfer` exports only `NebulaSettingsSchema.types` keys. iOS's settings contract/store/file transfer does not enumerate native account databases or MTProto keychains. No session serialization or restore UI was added to these flows.

## Required design before implementation

1. Keep a native per-account backup service separate from settings import/export, diagnostic reports and build artifacts. Operate only after explicit user enrollment. Never copy live SQLite/native files while writers are active.
2. Wrap a versioned minimal account envelope in authenticated encryption. On Android use a dedicated Android Keystore key; on iOS use an explicit Security Keychain item and suitable device-only access control, independent of the MTProto adapter. Use a fresh nonce for each encryption and bind account/format metadata as associated data.
3. State the recovery scope correctly. Android uninstall normally removes the app's Keystore keys, so a Keystore-only envelope cannot promise recovery after reinstall. A portable/reinstall archive would need a separately designed user-held recovery secret. iOS Keychain persistence/access also depends on device, team and access group; changing a development signing team is not a supported restore mechanism.
4. Restore into a fresh persistent account identity, never overwrite an existing account directory. Validate envelope version, complete key material, account identity and the native core's response before publishing the record or starting ordinary synchronization. Roll back an incomplete restore and its subscribers.
5. Logout deletes that installation's local backup/enrollment and retires the account record. A remotely revoked authorization must fail into normal login; a local archive cannot recreate a revoked server session. Never include the user's two-factor password in the envelope.
6. Verify crash recovery, wrong-device/key/passphrase, corruption, key loss, account duplication, logout/revocation and notification-key handling on signed Android/iOS devices with disposable test accounts. No real user credentials belong in fixtures or CI.

## Decision for this plan

The separate assessment is complete. A session-backup switch is not exposed: the current implementation has no tested secure recovery service or reinstall semantics on both platforms. Dynamic account storage, local pin order, edit-history cache and presentation settings are not substitutes for this service. The new account UI and settings backups do not export authorization keys.
