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
}
