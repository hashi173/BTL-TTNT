package com.coffeeshop;

import com.coffeeshop.util.EntityDisplayUtils;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityDisplayUtilsTest {

    @Test
    void buildReadableCodeNormalizesVietnameseText() {
        String code = EntityDisplayUtils.buildReadableCode("PRD", "Cà phê sữa đá", UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

        assertEquals("PRD-CA-PHE-SUA-DA", code);
    }

    @Test
    void resolveProductImagePathKeepsAbsoluteAssetsUntouched() {
        assertEquals("/images/products/CaffeLatte.png",
                EntityDisplayUtils.resolveProductImagePath("/images/products/CaffeLatte.png"));
        assertEquals("/uploads/products/menu.png",
                EntityDisplayUtils.resolveProductImagePath("/uploads/products/menu.png"));
        assertEquals("https://example.com/products/cappuccino.png",
                EntityDisplayUtils.resolveProductImagePath("https://example.com/products/cappuccino.png"));
    }

    @Test
    void resolveProductImagePathPrefixesBareFileNames() {
        assertEquals("/images/products/CaffeLatte.png",
                EntityDisplayUtils.resolveProductImagePath("CaffeLatte.png"));
        assertEquals("/images/no-image.png",
                EntityDisplayUtils.resolveProductImagePath(null));
    }
}
