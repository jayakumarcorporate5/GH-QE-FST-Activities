package selenium;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

public class actSelenium6 {
    public static void main(String[] args) {
        // Initialize the Firefox driver
        WebDriver driver = new FirefoxDriver();

        // Open the page
        driver.get("https://training-support.net/webelements/dynamic-controls");
        // Print the title of the page
        System.out.println("Page title: " + driver.getTitle());

        // Find the checkbox
        WebElement checkbox = driver.findElement(By.id("checkbox"));
        // Click it
        checkbox.click();
        // Check if it is selected
        System.out.println("Checkbox is selected: " + checkbox.isSelected());
        // Click the checkbox again
        checkbox.click();
        // Check if it is selected
        System.out.println("Checkbox is selected: " + checkbox.isSelected());

        // Close the browser
        driver.quit();
    }
}
