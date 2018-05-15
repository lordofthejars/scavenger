package me.escoffier.keynote;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class URLParsingTest {

    @Test
    public void test() {
        String url = "/v1/AUTH_gv0/image-aws/839212ed-2b1f-452f-9157-fb684cf937f4_ee95d096-5e9a-4d4a-bcac-488d6436e47f_93e13063-4eb5-427b-b2b7-409020f74a1c";

        String tx = url.substring(url.lastIndexOf("/") + 1);
        String prefix = url.substring(0, url.lastIndexOf("/"));
        String container = prefix.substring(prefix.lastIndexOf("/") + 1);

        assertThat(tx).isEqualTo("839212ed-2b1f-452f-9157-fb684cf937f4_ee95d096-5e9a-4d4a-bcac-488d6436e47f_93e13063-4eb5-427b-b2b7-409020f74a1c");
        assertThat(container).isEqualTo("image-aws");
    }
}
