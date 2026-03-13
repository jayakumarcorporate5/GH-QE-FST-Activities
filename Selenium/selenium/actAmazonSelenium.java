package selenium;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class actAmazonSelenium {
    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = new FirefoxDriver();
        driver.manage().window().maximize();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            // 1) Open Amazon.in
            driver.get("https://www.amazon.in/");
            waitForDocReady(wait);

            // 2) Search for "17 pro max"
            WebElement input = wait.until(ExpectedConditions.elementToBeClickable(By.id("twotabsearchtextbox")));
            input.clear();
            input.sendKeys("Iphone 17 pro max");
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(By.id("nav-search-submit-button")));
            btn.click();

            // 3) Wait for results container
            By resultsContainer = By.cssSelector("div.s-main-slot.s-result-list");
            wait.until(ExpectedConditions.presenceOfElementLocated(resultsContainer));

            // 4) Collect product tiles (skip banners) and keep ONLY iPhones
            List<WebElement> allCards = driver.findElements(By.cssSelector(
                    "div.s-main-slot div[data-component-type='s-search-result'][data-asin]"));

            List<WebElement> iphones = new ArrayList<>();
            for (WebElement card : allCards) {
                String title = getTitleFromCard(card);
                if (isBlank(title)) continue;

                if (!title.toLowerCase().contains("iphone")) continue; // <-- only iPhones

                // (Optional) Skip Sponsored tiles safely (no exceptions)
                boolean sponsored = !card.findElements(By.xpath(".//span[normalize-space()='Sponsored']")).isEmpty();
                if (sponsored) continue;

                // Keep cards that actually show a price node
                boolean hasPriceOffscreen = !card.findElements(
                        By.xpath(".//span[contains(@class,'a-price')]//span[contains(@class,'a-offscreen')]")).isEmpty();
                boolean hasPriceWhole = !card.findElements(By.cssSelector("span.a-price-whole")).isEmpty();
                if (!(hasPriceOffscreen || hasPriceWhole)) continue;

                iphones.add(card);
            }

            if (iphones.isEmpty()) {
                System.out.println("No iPhone tiles found on the first page.");
                return;
            }
            if (iphones.size() < 3) {
                System.out.println("Only " + iphones.size() + " iPhone result(s) found; selecting the last available.");
            }

            // 5) Pick the 3rd iPhone (0-based index 2)
            WebElement thirdCard = iphones.get(Math.min(2, iphones.size() - 1));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", thirdCard);

            // 6) Extract from the LIST TILE (preferred: quick)
            String titleOnTile = getTitleFromCard(thirdCard);
            String priceOnTile = getPriceFromCard(thirdCard);

            // Delivery on tiles is often missing without PIN; try to get a tight line
            String deliveryOnTile = getDeliveryFromCard(thirdCard);
            deliveryOnTile = firstDeliveryLine(deliveryOnTile); // sanitize to first delivery line
            if (isBlank(deliveryOnTile)) deliveryOnTile = "N/A on tile";

            // Normalize price for Windows console if rupee shows as '?'
            String pricePrintable = normalizeRupeeForConsole(priceOnTile);

            System.out.println("=== 3rd iPhone (From List Tile) ===");
            System.out.println("Title   : " + (isBlank(titleOnTile) ? "N/A" : titleOnTile));
            System.out.println("Price   : " + (isBlank(pricePrintable) ? "N/A" : pricePrintable));
            System.out.println("Delivery: " + deliveryOnTile);

            // 7) If delivery not on tile, open PDP and read there (no PIN needed)
            if ("N/A on tile".equals(deliveryOnTile)) {
                WebElement link = firstPresent(thirdCard,
                        By.cssSelector("h2 a"),
                        By.cssSelector("a.a-link-normal.s-underline-text"));
                if (link != null) {
                    String href = link.getAttribute("href");
                    if (!isBlank(href)) {
                        // Open in new tab to preserve search page
                        ((JavascriptExecutor) driver).executeScript("window.open(arguments[0],'_blank');", href);

                        // Switch to new tab
                        List<String> tabs = new ArrayList<>(driver.getWindowHandles());
                        driver.switchTo().window(tabs.get(tabs.size() - 1));

                        waitForDocReady(wait);

                        // Get price on PDP (more reliable)
                        String pdpPrice = firstNonBlankText(driver,
                                By.cssSelector("#corePriceDisplay_desktop_feature_div span.a-price > span.a-offscreen"),
                                By.cssSelector("span#priceblock_ourprice"),
                                By.cssSelector("span#priceblock_dealprice"),
                                By.cssSelector("span#priceblock_saleprice"),
                                By.xpath("//span[contains(@class,'a-price')]//span[contains(@class,'a-offscreen')]")
                        );
                        String pdpPricePrintable = normalizeRupeeForConsole(pdpPrice);

                        // Get delivery promise from PDP using tight targets first
                        String pdpDeliveryRaw = firstNonBlankText(driver,
                                // Primary delivery message blocks (newer layouts)
                                By.cssSelector("#mir-layout-DELIVERY_BLOCK-slot-PRIMARY_DELIVERY_MESSAGE_LARGE, #mir-layout-DELIVERY_BLOCK-slot-PRIMARY_DELIVERY_MESSAGE"),
                                By.id("deliveryBlockMessage"),
                                // Fallback: any span that contains 'Get it by' or 'delivery' (avoid grabbing big containers)
                                By.xpath("//span[contains(., 'Get it by')]"),
                                By.xpath("//span[contains(translate(., 'DELIVERY', 'delivery'), 'delivery')]")
                        );
                        String pdpDelivery = firstDeliveryLine(pdpDeliveryRaw);

                        System.out.println("=== From Product Page (PDP) ===");
                        System.out.println("Price   : " + (isBlank(pdpPricePrintable) ? "N/A" : pdpPricePrintable));
                        System.out.println("Delivery: " + (isBlank(pdpDelivery) ? "N/A" : pdpDelivery));
                    }
                }
            }

        } finally {
            Thread.sleep(1500);
            driver.quit();
        }
    }

    // ----------------- Helper methods -----------------

    private static void waitForDocReady(WebDriverWait wait) {
        wait.until(d -> ((JavascriptExecutor) d)
                .executeScript("return document.readyState").equals("complete"));
    }

    private static String getTitleFromCard(WebElement card) {
        String t = firstNonBlankText(card,
                By.xpath(".//h2//span[contains(@class,'a-text-normal')]"),
                By.xpath(".//span[contains(@class,'a-size-medium') and contains(@class,'a-color-base')]"),
                By.xpath(".//h2"));
        return isBlank(t) ? "" : t.trim();
    }

    private static String getPriceFromCard(WebElement card) {
        String p = firstNonBlankText(card,
                By.xpath(".//span[contains(@class,'a-price')]//span[contains(@class,'a-offscreen')]"));
        if (!isBlank(p)) return p.trim();

        String whole = firstNonBlankText(card, By.cssSelector("span.a-price-whole"));
        String frac  = firstNonBlankText(card, By.cssSelector("span.a-price-fraction"));
        if (!isBlank(whole)) {
            return (whole + (isBlank(frac) ? "" : "." + frac)).trim();
        }
        return "";
    }

    private static String getDeliveryFromCard(WebElement card) {
        // Try small, specific spans first (to avoid pulling full containers)
        String d = firstNonBlankText(card,
                By.xpath(".//span[contains(., 'Get it by')]"),
                By.xpath(".//span[contains(., 'FREE delivery')]"),
                By.xpath(".//span[contains(translate(., 'DELIVERY', 'delivery'), 'delivery')]"));
        return isBlank(d) ? "" : d.trim();
    }

    /** Return only the first line that looks like a delivery promise. */
    private static String firstDeliveryLine(String raw) {
        if (isBlank(raw)) return "";
        // Split by newlines or bullets, pick first line that looks like delivery
        String[] lines = raw.split("\\r?\\n|•|·");
        for (String ln : lines) {
            String s = ln.trim();
            String sLow = s.toLowerCase();
            if (sLow.contains("delivery") || sLow.startsWith("get it") || sLow.contains("arrives") || sLow.contains("by ")) {
                // strip excessive spaces
                s = s.replaceAll("\\s{2,}", " ").trim();
                // sometimes Amazon adds "FREE " prefix we can keep; just return the cleaned line
                return s;
            }
        }
        // If nothing matched, return compact first line
        return lines[0].trim();
    }

    /** Normalize ₹ for Windows console if it shows as '?' */
    private static String normalizeRupeeForConsole(String price) {
        if (isBlank(price)) return price;
        // If console can't render ₹, replace with "Rs." (you can also switch terminal to UTF-8)
        return price.replace("₹", "Rs.");
    }

    /** Return first element that exists among locators (within root). */
    private static WebElement firstPresent(WebElement root, By... locators) {
        for (By by : locators) {
            List<WebElement> els = root.findElements(by);
            if (!els.isEmpty()) return els.get(0);
        }
        return null;
    }

    /** Return first non-blank text among locators (within root). */
    private static String firstNonBlankText(WebElement root, By... locators) {
        for (By by : locators) {
            List<WebElement> els = root.findElements(by);
            if (!els.isEmpty()) {
                for (WebElement el : els) {
                    String txt = extractText(el);
                    if (!isBlank(txt)) return txt.trim();
                }
            }
        }
        return "";
    }

    /** Overload for root = driver. */
    private static String firstNonBlankText(WebDriver driver, By... locators) {
        for (By by : locators) {
            List<WebElement> els = driver.findElements(by);
            if (!els.isEmpty()) {
                for (WebElement el : els) {
                    String txt = extractText(el);
                    if (!isBlank(txt)) return txt.trim();
                }
            }
        }
        return "";
    }

    private static String extractText(WebElement el) {
        String t = el.getText();
        if (isBlank(t)) t = el.getAttribute("innerText");
        if (isBlank(t)) t = el.getAttribute("aria-label");
        return t == null ? "" : t.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}