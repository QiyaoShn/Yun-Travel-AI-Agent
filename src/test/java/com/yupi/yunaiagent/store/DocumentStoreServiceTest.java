package com.yupi.yunaiagent.store;

import com.yupi.yunaiagent.data.entity.TravelDocumentEntity;
import com.yupi.yunaiagent.data.mapper.TravelDocumentMapper;
import com.yupi.yunaiagent.store.DocumentStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档存储服务测试
 */
@SpringBootTest
class DocumentStoreServiceTest {

    @Autowired(required = false)
    private DocumentStoreService documentStoreService;

    @Autowired(required = false)
    private TravelDocumentMapper documentMapper;

    @Test
    void testLoadAllDocuments() {
        if (documentStoreService == null || documentMapper == null) {
            assertTrue(true, "MySQL 未配置，跳过测试");
            return;
        }

        List<TravelDocumentEntity> documents = documentStoreService.loadAllDocuments();
        assertNotNull(documents, "文档列表不应为空");
    }

    @Test
    void testGetDocumentById() {
        if (documentStoreService == null) {
            assertTrue(true, "MySQL 未配置，跳过测试");
            return;
        }

        // 测试存在和不存在的文档
        Optional<TravelDocumentEntity> notFound = documentStoreService.getDocumentById(-1L);
        assertTrue(notFound.isEmpty(), "不存在的文档应返回空");

        // 如果有数据，测试存在的文档
        List<TravelDocumentEntity> all = documentStoreService.loadAllDocuments();
        if (!all.isEmpty()) {
            Optional<TravelDocumentEntity> found = documentStoreService.getDocumentById(all.get(0).getId());
            assertTrue(found.isPresent(), "存在的文档应能被查询到");
        }
    }

    @Test
    void testLoadDocumentsByCategory() {
        if (documentStoreService == null) {
            assertTrue(true, "MySQL 未配置，跳过测试");
            return;
        }

        List<TravelDocumentEntity> scenicDocs = documentStoreService.loadDocumentsByCategory("景点");
        assertNotNull(scenicDocs, "类别查询结果不应为空");
    }
}
