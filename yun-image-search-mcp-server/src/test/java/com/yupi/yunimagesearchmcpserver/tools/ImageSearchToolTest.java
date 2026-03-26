package com.yupi.yunimagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    void searchImage(){
        String result = imageSearchTool.searchImage("中国四川省峨眉山景区");
        Assertions.assertNotNull(result);
    }

}
