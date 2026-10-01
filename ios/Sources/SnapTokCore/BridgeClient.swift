import Foundation
import Security

public enum BridgeCredential {
    private static var query: [String: Any] {
        var query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrService as String: "app.snaptok.bridge", kSecAttrAccount as String: "token"]
        #if os(iOS)
        if let group = Bundle.main.object(forInfoDictionaryKey: "SnapTokKeychainGroup") as? String {
            query[kSecAttrAccessGroup as String] = group
        }
        #endif
        return query
    }
    public static func read() -> String {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    public static func save(_ token: String) throws {
        let q = query
        if token.isEmpty { SecItemDelete(q as CFDictionary); return }
        let values: [String: Any] = [kSecValueData as String: Data(token.utf8), kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        var status = SecItemUpdate(q as CFDictionary, values as CFDictionary)
        if status == errSecItemNotFound { status = SecItemAdd(q.merging(values) { _, new in new } as CFDictionary, nil) }
        guard status == errSecSuccess else { throw SnapError.storage("Could not save the connection key securely (\(status)).") }
    }
}

public struct PreparedTransfer: Sendable {
    public var request: URLRequest
    public var title: String?
}

public struct BridgeClient {
    public init() {}
    public func prepare(_ job: DownloadJob, preferences: Preferences, token: String) async throws -> PreparedTransfer {
        if job.platform == .direct { return PreparedTransfer(request: URLRequest(url: job.source), title: nil) }
        guard !preferences.bridgeURL.isEmpty, !token.isEmpty else { throw SnapError.missingBridge }
        let base = try BridgeAddress.validate(preferences.bridgeURL)
        var request = URLRequest(url: base.appendingPathComponent("v1/jobs"))
        request.httpMethod = "POST"; request.timeoutInterval = 20
        request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["url": job.source.absoluteString,
            "quality": job.options.quality.rawValue, "max_bytes": job.options.maxBytes])
        // Never forward the connection key to a redirect destination.
        let session = URLSession(configuration: .ephemeral, delegate: NoRedirects(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 201,
              data.count < 65_536,
              let body = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let id = body["id"] as? String, UUID(uuidString: id) != nil else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw SnapError.transfer(code == 401 ? "The connection key was not accepted. Check it in Settings." : "The download bridge could not prepare this video (\(code)).")
        }
        var download = URLRequest(url: base.appendingPathComponent("v1/media/" + id))
        download.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        download.timeoutInterval = 180
        return PreparedTransfer(request: download, title: nil)
    }
    public func check(address: String, token: String) async throws {
        let base = try BridgeAddress.validate(address)
        var request = URLRequest(url: base.appendingPathComponent("v1/health")); request.timeoutInterval = 8
        request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
        let session = URLSession(configuration: .ephemeral, delegate: NoRedirects(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (_, response) = try await session.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw SnapError.transfer("The bridge did not accept this connection. Check its address and key.") }
    }
}

private final class NoRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
