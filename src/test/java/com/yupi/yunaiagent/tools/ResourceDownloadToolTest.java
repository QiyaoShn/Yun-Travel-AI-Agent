package com.yupi.yunaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ResourceDownloadToolTest {

    @Test
    public void testDownloadResource() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "https://gd-hbimg.huaban.com/9309dfe8d751a3ef2a0503753264b902bea181665224e0-gHsK06_fw658";
        String fileName = "logo.webp";
        String result = tool.downloadResource(url, fileName);
        assertNotNull(result);
    }
}

