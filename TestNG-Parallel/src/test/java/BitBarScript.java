import com.smartbear.visualtest.VisualTest;
import com.smartbear.visualtest.util.Utils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.ITestContext;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.openqa.selenium.Dimension;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class BitBarScript {
    public WebDriver driver;
    public String browser;
    public String url;
    protected String PROJECT_TOKEN;
    protected Dimension[] resolutions;
    private String deviceName;

    @BeforeTest
    @Parameters({"deviceOS", "url"})
    public void setup(String deviceOS, String url) throws Exception {
        this.url = url;
        Map<String, String> envMap = System.getenv();
        this.PROJECT_TOKEN = envMap.getOrDefault("PROJECT_TOKEN", "");
        String[] envVariables = {this.PROJECT_TOKEN, envMap.getOrDefault("HUB_URL", ""), envMap.getOrDefault("BITBAR_KEY", "")};
        if (Arrays.stream(envVariables).anyMatch(""::matches)){
            throw new Exception(String.format("Couldn't find .env file with keys PROJECT_TOKEN: %s, HUB_URL: %s, BITBAR_KEY: %s", envVariables[0], envVariables[1], envVariables[2]));
        }
        URL hubURL = new URL(envMap.getOrDefault("HUB_URL", ""));
        DesiredCapabilities capabilities = new DesiredCapabilities();
        String bitBarKey = envMap.getOrDefault("BITBAR_KEY", "");
        BitBarDevices devices = new BitBarDevices();
        String platform =  deviceOS.toLowerCase();
        browser = platform.equals("android") ? "Chrome" : "Safari";
        // Setting the api key
        capabilities.setCapability("bitbar_apiKey", bitBarKey);
        capabilities.setCapability("platformName", platform);
        capabilities.setCapability("deviceName", platform.equals("android") ? "Android Phone" : "iPhone device");
        capabilities.setCapability("automationName", platform.equals("android") ? "Appium" : "XCUITest");
        capabilities.setCapability("browserName", browser);
        capabilities.setCapability("bitbar_project", "Java Project");
        capabilities.setCapability("bitbar_testrun", "Java Test");
        List<HashMap<String, Object>> randomDevices = devices.getAvailableDevice(deviceOS);
        while(!randomDevices.isEmpty())
            try {
                HashMap<String, Object> device = randomDevices.remove(0);
                this.deviceName = (String) device.get("displayName");
                String devicePlatform = ((String) device.get("platform")).toLowerCase();
                String platformVersion = (String) ((HashMap<String, Object>) device.get("softwareVersion")).get("releaseVersion");
                Utils.logger.info("Selected devices: " + deviceName + " " + devicePlatform + " " + platformVersion);
                capabilities.setCapability("bitbar_device", device.get("displayName"));
                driver = new RemoteWebDriver(hubURL, capabilities);
                break;
            } catch (Exception e){
                System.out.println(e.getMessage());
                if (randomDevices.isEmpty()){
                    throw new Exception("Couldn't start BitBar Remote driver.");
                }
            }
    }

    @AfterTest
    public void tearDown() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    public void DemoScript(ITestContext ctx) throws Exception {
        String suiteName = ctx.getCurrentXmlTest().getSuite().getName();
        VisualTest visualTest = new VisualTest(driver, buildSettings(suiteName, browser));
        driver.get(this.url);
        Thread.sleep(2000); //some pages have a slight loading time
        String imageName = String.format("DemoProject-%s-%s", browser, deviceName);
        visualTest.capture(imageName);

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

    public class BitBarDevices {

        public List<HashMap<String, Object>> getAvailableDevice(String deviceOrGroupNames) throws Exception {
            BitBarApiClient apiClient = new BitBarApiClient(System.getenv("BITBAR_KEY"));
            List<Integer> deviceGroupIds = apiClient.getDeviceGroupsIds(deviceOrGroupNames);
            List<HashMap<String, Object>> devices;
            Random randomGenerator = new Random();
            if (deviceGroupIds != null){
                devices = new ArrayList<>(apiClient.findDeviceInGroups(deviceGroupIds));
                if (!devices.isEmpty()){
                    while(devices.size() > 3){
                        devices.remove(randomGenerator.nextInt(devices.size()));
                    }
                }else {
                    throw new Exception("There are no devices available");
                }
            }else {
                devices = apiClient.findDevice(deviceOrGroupNames);
                while(devices.size() > 3){
                    devices.remove(randomGenerator.nextInt(devices.size()));
                }
            }

            return devices;
        }
    }
    public class BitBarApiClient {
        private String BASE_URI = "https://cloud.bitbar.com/api";
        private String USER_SPECIFIC_URI = BASE_URI + "/v2/me";
        private String accessKey;

        public BitBarApiClient(String accessKey) {
            this.accessKey = accessKey;
        }

        public Object getDeviceGroupList() throws Exception {
            return queryApi("device-groups?withPublic=true", null, null);
        }

        public Object getDeviceListForGroup(String deviceGroupId) throws Exception {
            String path = "device-groups/" + deviceGroupId + "/devices";
            return queryApi(path, null, null);
        }

        public List<Integer> getDeviceGroupsIds(String deviceGroupNames) throws Exception {
            Map<String, String> query = new HashMap<String, String>() {{
                put("filter", "displayName_in_" + deviceGroupNames);
            }};

            JSONArray deviceGroups = queryApi("device-groups", query, null);
            if (deviceGroups.opt(0) == null || deviceGroups.length() == 0) {
                return null;
            } else {
                return deviceGroups.toList().stream().map(
                        (Object group) -> (Integer) ((HashMap) group).get("id")
                ).collect(Collectors.toList());
            }
        }

        public List<HashMap<String, Object>> findDeviceInGroups(List<Integer> deviceGroupIds) {
            List<HashMap<String, Object>> allDevices = new ArrayList<HashMap<String, Object>>();
            deviceGroupIds.forEach((Integer groupId) -> {
                String path = "device-groups/" + groupId + "/devices";
                Map<String, String> query = new HashMap<String, String>() {{
                    put("filter", "online_eq_true");
                }};
                try {
                    allDevices.addAll((queryApi(path, query, null)).toList().stream().map((Object o) -> (HashMap<String, Object>) o).collect(Collectors.toList()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            return allDevices.stream().filter((HashMap<String, Object> device) -> !((Boolean) device.get("locked"))).collect(Collectors.toList());
        }

        public List<HashMap<String, Object>> findDevice(String deviceName) throws Exception {
            String path = "devices";
            Map<String, String> query = new HashMap<String, String>() {{
                put("filter", "displayName_eq_" + deviceName + ";online_eq_true");
            }};
            List<HashMap<String, Object>> allDevices = (queryApi(path, query, null)).toList().stream().map((Object o) -> (HashMap<String, Object>) o).collect(Collectors.toList());
            if (!allDevices.isEmpty()) {
                List<HashMap<String, Object>> filteredDevices = allDevices.stream().filter((Object device) -> (!((JSONObject) device).optBoolean("locked"))).collect(Collectors.toList());
                if (filteredDevices.isEmpty()) {
                    throw new Exception("None of the devices with name " + deviceName + " are currently available");
                }
                return filteredDevices;
            }
            return allDevices;
        }

        public Object getDeviceSessionUiLink(String sessionId) throws Exception {
            Map<String, String> query = new HashMap<String, String>() {{
                put("filter", "clientSideId_eq_" + sessionId);
            }};
            List<Object> data = (queryApi("device-sessions", query, null)).toList();
            if (data.size() == 1) {
                return ((JSONObject) data.get(0)).get("uiLink");
            } else {
                throw new Exception("Failed to get UI link for session " + sessionId + ".  Expected exactly 1 device-session, found " + data.size());
            }
        }

        private JSONArray queryApi(String path, Map<String, String> query, String uri) throws Exception {
            if (uri == null) {
                uri = USER_SPECIFIC_URI;
            }
            if (query != null) {
                String encodedQuery = Utils.getParamsString(query);
                uri = uri + "/" + path + "?" + encodedQuery;
            } else {
                uri = uri + "/" + path;
            }
            String encoding = Base64.getEncoder().encodeToString((accessKey + ":" + "").getBytes());
            URL url = new URL(uri);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            JSONObject myObject = new JSONObject(content.toString());
            return myObject.getJSONArray("data");
        }
    }
}
