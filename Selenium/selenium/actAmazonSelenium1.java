package selenium;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Robust Amazon.in search: collects iPhone tiles across pages and prints the 3rd result.
 * - Uses ENTER submission to avoid overlay-blocked clicks.
 * - Waits for result tiles (not a fragile container).
 * - Detects Robot Check/CAPTCHA.
 * - Stores extracted data, not WebElements (prevents staleness after pagination).
 * - Extracts a clean delivery line like "FREE delivery Sun, 15 Mar".
 */
public class actAmazonSelenium1 {

    // Simple data holder to avoid stale WebElements across pages
    private static class ResultItem  {
        final String title;
        final String price;
        final String delivery;

        ResultItem(String title, String price, String delivery) {
            this.title = title;
            this.price = price;
            this.delivery = delivery;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        WebDriver driver = new FirefoxDriver();
        driver.manage().window().maximize();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

        // Start with a realistic model; you can pass your own via args
        String query = (args != null && args.length > 0)
                ? String.join(" ", args)
                : "iphone 15 pro max";

        try {
            // 1) Open Amazon and wait for DOM ready
            driver.get("https://www.amazon.in/");
            waitForDomReady(driver, wait);
            dismissOverlaysIfAny(driver); // best-effort

            // 2) Search (press ENTER to avoid popover covering the button)
            WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("twotabsearchtextbox")));
            searchBox.clear();
            searchBox.sendKeys(query);
            searchBox.sendKeys(Keys.ENTER);

            // 3) Wait until results navigation (URL) OR tiles present
            wait.until(d -> d.getCurrentUrl().contains("/s?k=") ||
                    !d.findElements(By.cssSelector("[data-component-type='s-search-result'][data-asin]")).isEmpty());

            waitForDomReady(driver, wait);

            // Detect Robot Check interstitial
            if (isRobotCheck(driver)) {
                System.out.println("Amazon presented a Robot Check / CAPTCHA page. Try slower actions or manual solve, then re-run.");
                return;
            }

            // Optional: small scroll to trigger lazy sections
            js(driver).executeScript("window.scrollTo(0, document.body.scrollHeight*0.15);");
            Thread.sleep(300);

            // 4) Collect iPhone tiles across pages until we have at least 3
            List<ResultItem> results = new ArrayList<>();
            int maxPagesToScan = 3;

            for (int page = 1; page <= maxPagesToScan && results.size() < 3; page++) {
                // Wait for at least one result tile (more stable than container)
                waitForAnyResults(driver, wait);

                List<WebElement> cards = driver.findElements(By.cssSelector(
                        "[data-component-type='s-search-result'][data-asin]:not([data-asin=''])"
                ));
                System.out.println("[DEBUG] Page " + page + " result card count: " + cards.size());

                for (WebElement card : cards) {
                    String title = firstNonBlankText(card,
                            By.cssSelector("h2 a.a-link-normal span"),
                            By.cssSelector("h2 span.a-text-normal"),
                            By.cssSelector("h2"));
                    if (title.isBlank()) continue;
                    if (!title.toLowerCase().contains("iphone")) continue;

                    // Extract price (formatted preferred), keep even if missing
                    String price = firstNonBlankText(card,
                            By.cssSelector("span.a-price > span.a-offscreen"),
                            By.cssSelector("span.a-price-whole"));
                    if (price.isBlank()) price = "N/A";
                    price = price.replace("₹", "Rs.");

                    // *** Extract a clean delivery line ***
                    String delivery = extractDeliveryFromTile(card);

                    results.add(new ResultItem(title.trim(), price.trim(), delivery.trim()));
                    if (results.size() >= 3) break;
                }

                // Go to next page only if we still need more
                if (results.size() < 3) {
                    String beforeUrl = driver.getCurrentUrl();
                    WebElement next = getNextButtonIfEnabled(driver);
                    if (next == null) {
                        System.out.println("[DEBUG] No next page or next disabled at page " + page);
                        break;
                    }
                    js(driver).executeScript("arguments[0].scrollIntoView({block:'center'});", next);
                    try {
                        next.click();
                    } catch (StaleElementReferenceException e) {
                        // As a fallback, click via JS if it became stale after scroll
                        js(driver).executeScript("arguments[0].click();", next);
                    }

                    // Wait for URL to change or fresh tiles to appear
                    wait.until(urlChangesFrom(beforeUrl));
                    waitForDomReady(driver, wait);

                    if (isRobotCheck(driver)) {
                        System.out.println("Robot Check encountered after pagination. Stopping.");
                        break;
                    }
                }
            }

            if (results.isEmpty()) {
                System.out.println("No iPhone results found.");
                return;
            }
            if (results.size() < 3) {
                System.out.println("Only " + results.size() + " iPhone result(s) found across scanned pages; selecting the last available.");
            }

            // 5) Select the 3rd (index 2) or last available and print
            ResultItem chosen = results.get(Math.min(2, results.size() - 1));

            System.out.println("=== 3rd iPhone (From List Tile) ===");
            System.out.println("Title   : " + chosen.title);
            System.out.println("Price   : " + chosen.price);
            System.out.println("Delivery: " + chosen.delivery);

        } finally {
            Thread.sleep(1000);
            driver.quit();
        }
    }

    // ---------- helpers ----------
    private static JavascriptExecutor js(WebDriver d) {
        return (JavascriptExecutor) d;
    }

    private static void waitForDomReady(WebDriver driver, WebDriverWait wait) {
        wait.until(d -> "complete".equals(js(d).executeScript("return document.readyState")));
    }

    private static void waitForAnyResults(WebDriver driver, WebDriverWait wait) {
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-component-type='s-search-result'][data-asin]")
        ));
    }

    private static ExpectedCondition<Boolean> urlChangesFrom(String beforeUrl) {
        return d -> !d.getCurrentUrl().equals(beforeUrl);
    }

    private static boolean isRobotCheck(WebDriver driver) {
        String title = driver.getTitle().toLowerCase();
        if (title.contains("robot check") || title.contains("enter the characters")) return true;
        return !driver.findElements(By.cssSelector("form[action*='validateCaptcha']")).isEmpty();
    }

    private static void dismissOverlaysIfAny(WebDriver driver) {
        try {
            // Cookie/consent & location popover (best-effort; selectors vary)
            List<By> candidates = List.of(
                By.id("sp-cc-accept"),                                   // cookies accept (EU style)
                By.cssSelector("input[name='accept']"),
                By.cssSelector("button[aria-label='Close']"),
                By.cssSelector("button[data-action='a-popover-close']"),  // popover close
                By.cssSelector("input[data-action-params*='GLUXCancelAction']") // location dialog cancel
            );
            for (By by : candidates) {
                List<WebElement> els = driver.findElements(by);
                if (!els.isEmpty() && els.get(0).isDisplayed()) {
                    els.get(0).click();
                    Thread.sleep(200);
                }
            }
        } catch (Exception ignored) {}
    }

    private static WebElement getNextButtonIfEnabled(WebDriver driver) {
        // Try several variants of the "Next" pagination selector
        List<By> nextByList = List.of(
            By.cssSelector("a.s-pagination-next.s-pagination-button"),
            By.cssSelector("a.s-pagination-item.s-pagination-next"),
            By.cssSelector("a.s-pagination-next")
        );
        for (By by : nextByList) {
            List<WebElement> cand = driver.findElements(by);
            for (WebElement e : cand) {
                String ariaDisabled = e.getAttribute("aria-disabled");
                boolean disabledClass = (e.getAttribute("class") != null) && e.getAttribute("class").contains("s-pagination-disabled");
                boolean hasHref = e.getAttribute("href") != null && !e.getAttribute("href").isBlank();
                boolean enabled = e.isDisplayed() && e.isEnabled() && !"true".equalsIgnoreCase(ariaDisabled) && !disabledClass && hasHref;
                if (enabled) return e;
            }
        }
        return null;
    }

    private static String safeText(WebElement el) {
        if (el == null) return "";
        String t = el.getText();
        if (t == null || t.isBlank()) t = el.getAttribute("innerText");
        if (t == null || t.isBlank()) t = el.getAttribute("aria-label");
        return t == null ? "" : t.trim();
    }

    private static String getFirstText(WebElement root, By by) {
        List<WebElement> els = root.findElements(by);
        return els.isEmpty() ? "" : safeText(els.get(0));
    }

    private static String firstNonBlankText(WebElement root, By... bys) {
        for (By by : bys) {
            String s = getFirstText(root, by);
            if (!s.isBlank()) return s;
        }
        return "";
    }

    /**
     * Extracts a concise delivery snippet from a search result tile.
     * Examples: "FREE delivery Sun, 15 Mar", "Get it by Mon, 16 Mar".
     * Returns "N/A" if not available on the tile without PIN.
     */
    private static String extractDeliveryFromTile(WebElement tile) {
        // Look for spans/divs/links mentioning "delivery" or "get it"
        List<WebElement> cands = tile.findElements(By.xpath(
            ".//*[self::span or self::div or self::a]" +
            "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'delivery') or " +
            " contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'get it')]"
        ));
        if (cands.isEmpty()) return "N/A";

        // Take the first visible candidate with some text
        String raw = "";
        for (WebElement el : cands) {
            String s = safeText(el);
            if (s != null && !s.isBlank()) {
                raw = s;
                break;
            }
        }
        if (raw.isBlank()) return "N/A";

        // Normalize whitespace
        String one = raw.replaceAll("\\s+", " ").trim();

        // Remove trailing junk like "Details", "Terms" etc.
        one = one.replaceAll("\\s*\\(?Details\\)?\\s*$", "").trim();
        one = one.replaceAll("\\s*\\(?Terms.*$", "").trim();

        // If text is very long (some templates spill extra info), keep only the first clause that contains delivery words
        // Prefer chunks starting with "FREE delivery" or containing "Get it"
        String lower = one.toLowerCase();
        if (lower.contains("free delivery")) {
            int idx = lower.indexOf("free delivery");
            one = one.substring(idx).trim();
        } else if (lower.contains("get it")) {
            int idx = lower.indexOf("get it");
            one = one.substring(idx).trim();
        } else if (lower.contains("delivery")) {
            int idx = lower.indexOf("delivery");
            one = one.substring(Math.max(0, idx - 6)).trim(); // keep a bit before "delivery" if needed
        }

        // If the line still contains multiple sentences, keep the first sentence-like chunk
        int dot = one.indexOf(". ");
        if (dot > 10) { // avoid dots in abbreviations at the very start
            one = one.substring(0, dot).trim();
        }

        // Be defensive: if still too long (sometimes contains extra marketing text), cut after ~60-90 chars at a comma
        if (one.length() > 110) {
            int cut = one.indexOf(",", 40);
            if (cut > 0 && cut < 110) {
                one = one.substring(0, cut + 1).trim();
            } else {
                one = one.substring(0, Math.min(110, one.length())).trim();
            }
        }

        return one.isBlank() ? "N/A" : one;
    }

    private static String firstDeliveryLine(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int nl = s.indexOf('\n');
        if (nl > 0) s = s.substring(0, nl).trim();
        s = s.replaceAll("\\s{2,}", " ");
        return s;
    }
}