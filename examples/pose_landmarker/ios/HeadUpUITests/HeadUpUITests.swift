import XCTest

final class HeadUpUITests: XCTestCase {
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--uitesting", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(app.buttons["settingsButton"].waitForExistence(timeout: 15))
        return app
    }
    func testAllFourTabsAndSettings() {
        let app = launch()
        for label in ["Stats", "Challenge", "Shop", "Scan"] {
            let tab = app.tabBars.buttons[label]
            XCTAssertTrue(tab.exists); tab.tap()
            XCTAssertTrue(app.buttons["monitoringToggle"].firstMatch.exists)
        }
        app.buttons["settingsButton"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["monitoringToggle"].firstMatch.exists)
    }
    func testLanguageAndResetFlow() {
        let app = launch()
        app.buttons["settingsButton"].tap()
        app.buttons["languagePicker"].tap()
        app.buttons["繁體中文"].tap()
        XCTAssertTrue(app.navigationBars["設定"].waitForExistence(timeout: 5))
        app.swipeUp()
        app.buttons["重設所有本機資料"].tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        app.alerts.buttons["取消"].tap()
        XCTAssertFalse(app.alerts.firstMatch.exists)
    }
    func testCameraUnavailableDoesNotCreateFakeMonitoringTime() {
        let app = launch()
        app.buttons["monitoringToggle"].firstMatch.tap()
        let unavailable = app.staticTexts["Camera is unavailable"]
        XCTAssertTrue(unavailable.waitForExistence(timeout: 15))
        app.tabBars.buttons["Stats"].tap()
        XCTAssertTrue(app.staticTexts["A fresh start"].waitForExistence(timeout: 5))
    }

    func testFloatingWindowRequiresActiveCamera() {
        let app = launch()
        let button = app.buttons["pictureInPictureToggle"]
        for _ in 0..<3 where !button.isHittable { app.swipeUp() }
        XCTAssertTrue(button.exists)
        XCTAssertFalse(button.isEnabled)
        #if targetEnvironment(simulator)
        XCTAssertTrue(app.staticTexts["pictureInPictureDeviceHint"].exists)
        #endif
    }

    func testLiveActivityPreferenceAndTodayLink() {
        let app = launch()
        app.buttons["settingsButton"].tap()
        let setting = app.switches["liveActivitiesToggle"]
        XCTAssertTrue(setting.waitForExistence(timeout: 5))
        XCTAssertEqual(setting.value as? String, "1")
        setting.tap()
        XCTAssertEqual(setting.value as? String, "0")
        app.open(URL(string: "headup://today")!)
        XCTAssertTrue(app.tabBars.buttons["Stats"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.tabBars.buttons["Stats"].isSelected)
        app.buttons["settingsButton"].tap()
        XCTAssertEqual(setting.value as? String, "0")
    }

    func testDeviceDynamicIslandTodayAndPause() throws {
        #if targetEnvironment(simulator) || !HEADUP_PIP_PREVIEW
        throw XCTSkip("Requires PiP Preview and a physical iPhone with Dynamic Island.")
        #else
        let app = launch()
        app.buttons["monitoringToggle"].firstMatch.tap()
        let toggle = app.buttons["pictureInPictureToggle"]
        for _ in 0..<3 where !toggle.isHittable { app.swipeUp() }
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: toggle)
        waitForExpectations(timeout: 20)
        toggle.tap()
        XCTAssertTrue(app.staticTexts["Small window active. You can switch apps now."].waitForExistence(timeout: 15))
        XCUIDevice.shared.press(.home)
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        sleep(2)
        system.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.035)).press(forDuration: 1)
        let today = system.buttons["activityViewToday"]
        XCTAssertTrue(today.waitForExistence(timeout: 8))
        let expanded = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        expanded.name = "Dynamic Island expanded — real camera"
        expanded.lifetime = .keepAlways
        add(expanded)
        today.tap()
        XCTAssertTrue(app.tabBars.buttons["Stats"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Stats"].isSelected)
        XCUIDevice.shared.press(.home)
        sleep(2)
        system.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.035)).press(forDuration: 1)
        let pause = system.buttons["activityPauseMonitoring"]
        XCTAssertTrue(pause.waitForExistence(timeout: 8))
        pause.tap()
        sleep(2)
        app.activate()
        XCTAssertTrue(app.buttons["monitoringToggle"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["monitoringToggle"].firstMatch.label.contains("Start"))
        XCTAssertTrue(app.staticTexts["Monitoring paused"].exists)
        #endif
    }

    func testDevicePiPReceivesFramesWhileUsingAnotherApp() throws {
        #if targetEnvironment(simulator) || !HEADUP_PIP_PREVIEW
        throw XCTSkip("Requires the PiP Preview configuration and a physical device.")
        #else
        let app = XCUIApplication()
        app.launchArguments = ["--uitesting", "--pip-device-test", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(app.buttons["monitoringToggle"].firstMatch.waitForExistence(timeout: 20))
        app.buttons["monitoringToggle"].firstMatch.tap()
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        if system.alerts.firstMatch.waitForExistence(timeout: 3) {
            for title in ["Allow", "允許", "OK", "好"] where system.alerts.buttons[title].exists {
                system.alerts.buttons[title].tap()
                break
            }
        }
        let toggle = app.buttons["pictureInPictureToggle"]
        for _ in 0..<3 where !toggle.isHittable { app.swipeUp() }
        let ready = NSPredicate(format: "enabled == true")
        expectation(for: ready, evaluatedWith: toggle)
        waitForExpectations(timeout: 20)
        toggle.tap()
        XCTAssertTrue(app.staticTexts["Small window active. You can switch apps now."].waitForExistence(timeout: 15))
        XCUIDevice.shared.press(.home)
        XCUIApplication(bundleIdentifier: "com.apple.mobilesafari").activate()
        sleep(8)
        app.activate()
        let count = app.staticTexts["backgroundFrameCount"]
        XCTAssertTrue(count.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(Int(count.label) ?? 0, 3, "Expected real camera inference callbacks while Safari was foreground.")
        let beforePause = Int(count.label) ?? 0
        if toggle.label == "Close small window" { toggle.tap() }
        app.buttons["monitoringToggle"].firstMatch.tap()
        XCUIDevice.shared.press(.home)
        sleep(3)
        app.activate()
        XCTAssertEqual(Int(count.label) ?? 0, beforePause, "Paused monitoring must not process background frames.")
        #endif
    }
}
