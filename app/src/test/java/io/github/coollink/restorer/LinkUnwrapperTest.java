package io.github.coollink.restorer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinkUnwrapperTest {
    @Test
    public void unwrapsRealExample() {
        assertEquals(
                "https://alist.1mxy.cn/139",
                LinkUnwrapper.unwrapUrl(
                        "https://www.coolapk.com/link?url=https%3A%2F%2Falist.1mxy.cn%2F139"));
    }

    @Test
    public void leavesNormalCoolapkUrlAlone() {
        String url = "https://www.coolapk.com/feed/123?shareKey=abc";
        assertEquals(url, LinkUnwrapper.unwrapUrl(url));
    }

    @Test
    public void supportsDoubleEncoding() {
        assertEquals(
                "https://example.com/a?x=1",
                LinkUnwrapper.unwrapUrl(
                        "https://www.coolapk.com/link?url=https%253A%252F%252Fexample.com%252Fa%253Fx%253D1"));
    }

    @Test
    public void rewritesTextAndKeepsPunctuation() {
        String input = "下载：https://www.coolapk.com/link?url=https%3A%2F%2Fexample.com%2Fa。";
        assertEquals("下载：https://example.com/a。", LinkUnwrapper.rewriteText(input));
    }

    @Test
    public void ignoresOtherDomains() {
        assertFalse(LinkUnwrapper.needsRewrite("https://example.com/link?url=https://a.com"));
        assertTrue(LinkUnwrapper.needsRewrite(
                "https://www.coolapk.com/link?url=https%3A%2F%2Fa.com"));
    }
}
