import com.smartbear.visualtest.VisualTest;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.openqa.selenium.Dimension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DemoScript {
    public WebDriver driver;
    public String browser;
    public String url;
    protected String PROJECT_TOKEN;
    protected Dimension[] resolutions;

    @BeforeTest
    @Parameters({"browser", "resolutions", "url"})
    public void setup(String browser, String resolutionsCSV, String url) {
        this.url = url;
        this.browser = browser;
        Map<String, String> envMap = System.getenv();
        this.PROJECT_TOKEN = envMap.getOrDefault("PROJECT_TOKEN", "");
        this.resolutions = Arrays.stream(resolutionsCSV.split(",")).map((resolution) ->new Dimension(Integer.valueOf(resolution.split("x")[0]), Integer.valueOf(resolution.split("x")[1]))).toArray(Dimension[]::new);
    }

    @AfterTest
    public void tearDown() {
        driver.close();
    }

    @Test(invocationCount = 10, threadPoolSize = 10)
    public void DemoScript(ITestContext ctx) throws Exception {
        driver = setupBrowserDriver(browser, driver);
        String suiteName = ctx.getCurrentXmlTest().getSuite().getName();
        VisualTest visualTest = new VisualTest(driver, buildSettings(suiteName, browser));
        for (Dimension resolution : this.resolutions) {
            driver.manage().window().setSize(resolution);
            driver.get(this.url);
            Thread.sleep(2000); //some pages have a slight loading time
            String imageName = String.format("DemoProject-%s-%s", browser, resolution);
            visualTest.capture(imageName);
        }
    }

    public HashMap<String, Object> buildSettings(String suiteName, String browser) {
        HashMap<String, Object> settings = new HashMap<>();
        settings.put("testRunName", suiteName + ": " + browser);
        settings.put("debug", false);
        settings.put("projectToken", this.PROJECT_TOKEN);

        Path currentPath = Paths.get(System.getProperty("user.dir"));
        Path filePath = Paths.get(currentPath.toString(), String.format("src/test/resources/screenshots/%s", suiteName));
        settings.put("saveTo", filePath.toString());
        return settings;
    }

    public static WebDriver setupBrowserDriver(String browser, WebDriver driver){
        int i = 0;
        boolean passed = false;
        while(!passed && i < 3) {
            try {
                if (browser.equalsIgnoreCase("firefox")) {
                    WebDriverManager.firefoxdriver().setup();
                    FirefoxOptions options = new FirefoxOptions();
                    options.setHeadless(true);
                    driver = new FirefoxDriver(options);
                } else if (browser.equalsIgnoreCase("chrome")) {
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("--remote-allow-origins=*");
                    options.addArguments("--disable-gpu");
                    // Mobile web capabilities
                    options.setHeadless(true);
                    driver = new ChromeDriver(options);
                } else if (browser.equalsIgnoreCase("safari")) {
                    if(!System.getProperty("os.name").equals("Mac OS X")){
                        throw new SkipException("Skipping this test");
                    }
                    WebDriverManager.safaridriver().setup();
                    SafariOptions options = new SafariOptions();
                    driver = new SafariDriver(options);
                } else if (browser.equalsIgnoreCase("edge")) {
                    if(System.getProperty("os.name").equals("Linux")){
                        throw new SkipException("Skipping this test");
                    }
                    WebDriverManager.edgedriver().setup();
                    EdgeOptions options = new EdgeOptions();
                    options.addArguments("--disable-gpu");
                    options.addArguments("--remote-allow-origins=*");
                    options.setHeadless(true);
                    driver = new EdgeDriver(options);
                } else {
                    WebDriverManager.chromedriver().setup();
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("--remote-allow-origins=*");
                    options.addArguments("--disable-gpu");
                    options.setHeadless(true);
                    driver = new ChromeDriver(options);
                }
                passed = true;
            }catch (SkipException se){
                throw new SkipException("Skip this test");
            }
            catch (Exception e){
                e.printStackTrace();
                i++;
            }
        }
        return driver;
    }
}
