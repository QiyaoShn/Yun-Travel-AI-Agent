package com.yupi.yunaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TravelDocumentLoaderTest {

    @Resource
    private TravelDocumentLoader travelDocumentLoader;

    @Test
    void loadMarkdowns() {
        travelDocumentLoader.loadMarkdowns();
    }
}