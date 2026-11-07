package mobile_testing_app;


import mobile_testing_app.MobileTesting;

import io.appium.java_client.TouchAction;
import io.appium.java_client.android.*;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.PointOption;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.openqa.selenium.interactions.*;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Assignment {
	private static String[] loginPageElements = {"com.experitest.ExperiBank:id/usernameTextField", "com.experitest.ExperiBank:id/passwordTextField", "com.experitest.ExperiBank:id/loginButton"};
	private static String[] summaryPageElements = {"com.experitest.ExperiBank:id/makePaymentButton", "com.experitest.ExperiBank:id/logoutButton"};
	private static String[] paymentPageElements = {"com.experitest.ExperiBank:id/phoneTextField", "com.experitest.ExperiBank:id/nameTextField", "com.experitest.ExperiBank:id/nameTextField",
											"com.experitest.ExperiBank:id/amountLabel", "com.experitest.ExperiBank:id/amount", "com.experitest.ExperiBank:id/countryTextField",
											"com.experitest.ExperiBank:id/countryButton", "com.experitest.ExperiBank:id/sendPaymentButton", "com.experitest.ExperiBank:id/cancelButton"};


	/* Helpers */

	private static final String APP_ID = "com.experitest.ExperiBank:id/";

	// Locate an element by its resource-id (same style as Main.waitForLoad)
	private static By byId(String name) {
		return By.xpath("//*[@resource-id=\'" + APP_ID + name + "\']");
	}

	// Let the app settle (used when the expected result is that nothing changes)
	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	// The soft keyboard can cover buttons; hide it only if it is actually shown
	private static void hideKeyboard(AndroidDriver<AndroidElement> driver) {
		try {
			driver.hideKeyboard();
		} catch (WebDriverException ex) {
			// keyboard was not shown
		}
	}

	// Ensure that every element of a page is visible, i.e. that we are on that page
	private static void requirePage(WebDriverWait wait, String[] pageElements, String pageName) throws NoSuchElementException {
		try {
			for (int i = 0; i < pageElements.length; i++) {
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@resource-id=\'" + pageElements[i] + "\']")));
			}
		} catch (TimeoutException ex) {
			throw new NoSuchElementException("Not on the " + pageName + " page.");
		}
	}

	// Ensure that we are on the summary page and that the balance shows the given amount
	private static void requireSummaryWithBalance(WebDriverWait wait, String amount) throws NoSuchElementException {
		requirePage(wait, summaryPageElements, "summary");
		try {
			// the balance is shown inside a WebView, so it is exposed as content-desc rather than text
			wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@content-desc,\'" + amount + "\') or contains(@text,\'" + amount + "\')]")));
		} catch (TimeoutException ex) {
			throw new NoSuchElementException("Summary page does not show $" + amount + ".");
		}
	}

	// Current value shown for the amount slider, or null when it cannot be read
	private static Integer readAmount(AndroidDriver<AndroidElement> driver) {
		By[] places = {byId("amountLabel"), byId("amount")};
		Pattern number = Pattern.compile("(\\d+)");
		for (int i = 0; i < places.length; i++) {
			try {
				String text = driver.findElement(places[i]).getText();
				if (text != null) {
					Matcher m = number.matcher(text);
					if (m.find()) {
						return Integer.valueOf(m.group(1));
					}
				}
			} catch (WebDriverException ex) {
				// try the next place
			}
		}
		return null;
	}

	// Drag the amount slider (range 0-100) to the given value, correcting from the value shown
	private static void dragSlider(AndroidDriver<AndroidElement> driver, int target) throws NoSuchElementException {
		AndroidElement slider = driver.findElement(byId("amount"));
		Point location = slider.getLocation();
		Dimension size = slider.getSize();
		int y = location.getY() + size.getHeight() / 2;
		double pixelsPerUnit = size.getWidth() / 100.0;
		int x = location.getX() + (int) Math.round(pixelsPerUnit * target);

		for (int attempt = 0; attempt < 6; attempt++) {
			new TouchAction(driver)
				.press(PointOption.point(x, y))
				.waitAction(WaitOptions.waitOptions(Duration.ofMillis(200)))
				.release()
				.perform();
			pause(400);

			Integer shown = readAmount(driver);
			if (shown == null || shown.intValue() == target) {
				return;
			}
			x += (int) Math.round(pixelsPerUnit * (target - shown.intValue()));
		}
		throw new NoSuchElementException("Slider did not reach $" + target + ".");
	}

	// An alert is present if the driver can switch to it, or its positive button is on screen
	private static boolean alertPresent(AndroidDriver<AndroidElement> driver) {
		try {
			driver.switchTo().alert();
			return true;
		} catch (WebDriverException ex) {
			return !driver.findElements(By.id("android:id/button1")).isEmpty();
		}
	}

	private static void waitForAlert(AndroidDriver<AndroidElement> driver, WebDriverWait wait, String message) throws NoSuchElementException {
		try {
			wait.until(d -> alertPresent(driver));
		} catch (TimeoutException ex) {
			throw new NoSuchElementException(message);
		}
	}

	// Accept (positive button) or dismiss (negative button) the alert
	private static void answerAlert(AndroidDriver<AndroidElement> driver, boolean accept) {
		try {
			if (accept) {
				driver.switchTo().alert().accept();
			} else {
				driver.switchTo().alert().dismiss();
			}
		} catch (WebDriverException ex) {
			driver.findElement(By.id(accept ? "android:id/button1" : "android:id/button2")).click();
		}
	}

	private static void pressKey(AndroidDriver<AndroidElement> driver, AndroidKey key) {
		driver.pressKey(new KeyEvent(key));
	}


	/* Start Here */

	public static void run() throws NoSuchElementException {
		AndroidDriver<AndroidElement> driver = (AndroidDriver<AndroidElement>) MobileTesting.getDriver();

		long timeout = 6; // 6 seconds
		WebDriverWait wait = new WebDriverWait(driver, timeout);

		/* Start testing */
		AndroidElement element;
		Actions action = new Actions(driver);
		Alert alert;

		// (1) Check if on login page
		requirePage(wait, loginPageElements, "login");

		MobileTesting.printTest(1);

		// (2) Type the string "company" to the username field
		element = driver.findElement(byId("usernameTextField"));
		element.sendKeys("company");


		MobileTesting.printTest(2);

		// (3) Focus the password field
		element = driver.findElement(byId("passwordTextField"));
		element.click();


		MobileTesting.printTest(3);

		// (4) Type the string "company" to the password field
		element = driver.findElement(byId("passwordTextField"));
		element.sendKeys("company");


		MobileTesting.printTest(4);

		// (5) Focus the username field
		element = driver.findElement(byId("usernameTextField"));
		element.click();


		MobileTesting.printTest(5);

		// (6) Empty out the username field
		element = driver.findElement(byId("usernameTextField"));
		element.clear();


		MobileTesting.printTest(6);

		// (7) Press the login button and ensure still on login page
		hideKeyboard(driver);
		element = driver.findElement(byId("loginButton"));
		element.click();
		pause(1500);
		requirePage(wait, loginPageElements, "login");


		MobileTesting.printTest(7);

		// (8) Re-type "company" to the username field
		element = driver.findElement(byId("usernameTextField"));
		element.sendKeys("company");


		MobileTesting.printTest(8);

		// (9) Press the login button and ensure now on summary page with $100
		hideKeyboard(driver);
		element = driver.findElement(byId("loginButton"));
		element.click();
		requireSummaryWithBalance(wait, "100");


		MobileTesting.printTest(9);

		// (10) Press the make payment button and ensure now on payment page
		element = driver.findElement(byId("makePaymentButton"));
		element.click();
		requirePage(wait, paymentPageElements, "payment");


		MobileTesting.printTest(10);

		// (11) Type the string "612-555-0112" to the phone field
		element = driver.findElement(byId("phoneTextField"));
		element.sendKeys("612-555-0112");


		MobileTesting.printTest(11);

		// (12) Type the string "Alice" to the name text field
		element = driver.findElement(byId("nameTextField"));
		element.sendKeys("Alice");


		MobileTesting.printTest(12);

		// (13) Drag the amount slider to $35
		hideKeyboard(driver);
		dragSlider(driver, 35);


		MobileTesting.printTest(13);

		// (14) Press the send payment button and ensure still on payment page
		hideKeyboard(driver);
		element = driver.findElement(byId("sendPaymentButton"));
		element.click();
		pause(1500);
		requirePage(wait, paymentPageElements, "payment");


		MobileTesting.printTest(14);

		// (15) Type the string "United States" to the country text field
		element = driver.findElement(byId("countryTextField"));
		element.sendKeys("United States");


		MobileTesting.printTest(15);

		// (16) Press the cancel button and ensure now on summary page with $100
		hideKeyboard(driver);
		element = driver.findElement(byId("cancelButton"));
		element.click();
		requireSummaryWithBalance(wait, "100");


		MobileTesting.printTest(16);

		// (17) Press the make payment button and ensure now on payment page with default values
		element = driver.findElement(byId("makePaymentButton"));
		element.click();
		requirePage(wait, paymentPageElements, "payment");
		if (driver.findElement(byId("phoneTextField")).getText().contains("612-555-0112")
				|| driver.findElement(byId("nameTextField")).getText().contains("Alice")
				|| driver.findElement(byId("countryTextField")).getText().contains("United States")) {
			throw new NoSuchElementException("Payment page does not show default values.");
		}


		MobileTesting.printTest(17);

		// (18) Type the string "612-555-0355" to the phone field
		element = driver.findElement(byId("phoneTextField"));
		element.sendKeys("612-555-0355");


		MobileTesting.printTest(18);

		// (19) Type the string "Bob" to the name text field
		element = driver.findElement(byId("nameTextField"));
		element.sendKeys("Bob");


		MobileTesting.printTest(19);

		// (20) Drag the amount slider to $55
		hideKeyboard(driver);
		dragSlider(driver, 55);


		MobileTesting.printTest(20);

		// (21) Press the country button
		element = driver.findElement(byId("countryButton"));
		element.click();
		try {
			wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(@class,\'ListView\') or contains(@class,\'RecyclerView\')]")));
		} catch (TimeoutException ex) {
			throw new NoSuchElementException("Country list did not open.");
		}


		MobileTesting.printTest(21);

		// (22) Select France from the country list (13th on the list)
		try {
			// scroll the list until France is on screen, then select it
			element = driver.findElementByAndroidUIAutomator(
				"new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"France\"))");
			element.click();
		} catch (WebDriverException ex) {
			// fall back on the down arrow to the 13th element, then select it
			for (int i = 0; i < 13; i++) {
				pressKey(driver, AndroidKey.DPAD_DOWN);
			}
			pressKey(driver, AndroidKey.ENTER);
		}
		requirePage(wait, paymentPageElements, "payment");


		MobileTesting.printTest(22);

		// (23) Press the send payment button and ensure alert appears
		hideKeyboard(driver);
		element = driver.findElement(byId("sendPaymentButton"));
		element.click();
		waitForAlert(driver, wait, "Alert did not appear.");


		MobileTesting.printTest(23);

		// (24) Dismiss the alert and ensure now on payment page
		answerAlert(driver, false);
		requirePage(wait, paymentPageElements, "payment");


		MobileTesting.printTest(24);

		// (25) Press the send payment button and ensure alert appears again
		element = driver.findElement(byId("sendPaymentButton"));
		element.click();
		waitForAlert(driver, wait, "Alert did not appear again.");


		MobileTesting.printTest(25);

		// (26) Accept the alert and ensure now on summary page
		answerAlert(driver, true);
		requirePage(wait, summaryPageElements, "summary");


		MobileTesting.printTest(26);

		// (27) Press the logout button and ensure now on login page
		element = driver.findElement(byId("logoutButton"));
		element.click();
		requirePage(wait, loginPageElements, "login");


		MobileTesting.printTest(27);
	}
}
