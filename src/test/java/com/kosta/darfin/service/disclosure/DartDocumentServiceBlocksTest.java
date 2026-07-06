package com.kosta.darfin.service.disclosure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosta.darfin.dto.disclosure.DartDocumentResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DartDocumentServiceBlocksTest {

    @Test
    void parseBlocksMatchesPythonSampleResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getClassLoader().getResourceAsStream("doc_resp2.json");
        JsonNode root = mapper.readTree(is);
        String text = root.path("text").asText();

        DartDocumentService service = new DartDocumentService(new RestTemplate(), mapper);
        Method parseBlocks = DartDocumentService.class.getDeclaredMethod("parseBlocks", JsonNode.class);
        parseBlocks.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<DartDocumentResponseDto.Block> blocks =
                (List<DartDocumentResponseDto.Block>) parseBlocks.invoke(service, root.path("blocks"));

        assertEquals(root.path("blocks").size(), blocks.size());

        int tableCount = 0;
        for (DartDocumentResponseDto.Block b : blocks) {
            if ("table".equals(b.getType())) {
                tableCount++;
                assertTrue(b.getRows() != null && !b.getRows().isEmpty());
                if (b.getCharStart() != null) {
                    StringBuilder expected = new StringBuilder();
                    for (int ri = 0; ri < b.getRows().size(); ri++) {
                        if (ri > 0) expected.append("\n");
                        expected.append(String.join(" | ", b.getRows().get(ri)));
                    }
                    String actual = text.substring(b.getCharStart(), b.getCharEnd());
                    assertEquals(expected.toString(), actual, "table block offset mismatch");
                }
            } else {
                if (b.getCharStart() != null) {
                    String actual = text.substring(b.getCharStart(), b.getCharEnd());
                    assertEquals(b.getText(), actual, "paragraph block offset mismatch");
                }
            }
        }
        assertTrue(tableCount > 0, "sample response should contain at least one table block");
        System.out.println("Parsed " + blocks.size() + " blocks, " + tableCount + " tables. All offsets matched.");
    }
}
