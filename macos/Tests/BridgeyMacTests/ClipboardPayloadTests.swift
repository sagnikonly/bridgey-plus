import AppKit
import XCTest
@testable import BridgeyMac

final class ClipboardPayloadTests: XCTestCase {
    func testRichClipboardRequiresPlainFallbackAndHTML() {
        XCTAssertNil(RichClipboardContent(text: "text", html: nil))
        XCTAssertNil(RichClipboardContent(text: "", html: "<b>text</b>"))
        XCTAssertEqual(RichClipboardContent(text: "text", html: "<b>text</b>")?.html, "<b>text</b>")
    }

    func testClipboardContentIsBounded() {
        XCTAssertTrue(clipboardTextFits(String(repeating: "x", count: maximumClipboardContentBytes)))
        XCTAssertNil(RichClipboardContent(
            text: String(repeating: "x", count: maximumClipboardContentBytes),
            html: "<b>x</b>"
        ))
    }

    func testAutoSyncSkipsConcealedAndTransientPasteboardTypes() {
        XCTAssertTrue(shouldAutoSyncPasteboard(types: [.string, .html]))
        XCTAssertFalse(shouldAutoSyncPasteboard(types: [.string, NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")]))
        XCTAssertFalse(shouldAutoSyncPasteboard(types: [.string, NSPasteboard.PasteboardType("org.nspasteboard.TransientType")]))
        XCTAssertFalse(shouldAutoSyncPasteboard(types: [.string, NSPasteboard.PasteboardType("com.agilebits.onepassword")]))
    }

    func testClipboardSnapshotEquality() {
        XCTAssertEqual(
            ClipboardSnapshot(text: "hello", html: "<b>hello</b>"),
            ClipboardSnapshot(text: "hello", html: "<b>hello</b>")
        )
        XCTAssertNotEqual(
            ClipboardSnapshot(text: "hello", html: nil),
            ClipboardSnapshot(text: "hello", html: "<b>hello</b>")
        )
    }
}
