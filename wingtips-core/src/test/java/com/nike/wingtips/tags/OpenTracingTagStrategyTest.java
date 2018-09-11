package com.nike.wingtips.tags;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.eq;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.nike.wingtips.Span;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

@RunWith(DataProviderRunner.class)
public class OpenTracingTagStrategyTest {

    private Map<String,String> spanTags = new HashMap<String,String>();

    private Span spanMock;
    private Object nullObj = null; // No need for this to have a value

    private boolean isErroredResponse;
    final String responseStatus = "200";
    final String httpUrl = "/endpoint";
    final String httpMethod = "GET";

    @Before
    public void setup() {
        spanMock = mock(Span.class);

        spanTags.clear();
        doReturn(spanTags).when(spanMock).getTags();
    }

    @DataProvider(value = {
            "true",
            "false"
    }, splitBy = "\\|")
    @Test
    public void tagspanwithresponseattributes_behaves_as_expected(boolean isErroredResponse) {
        // given
        this.isErroredResponse = isErroredResponse;

        // when
        openTracingTagStrategy.tagSpanWithResponseAttributes(spanMock, nullObj);

        // then
        verify(spanMock).putTag(eq(KnownOpenTracingTags.HTTP_STATUS), eq(responseStatus));

        if(isErroredResponse) {
            // the error tag should only have a value if isErroredResponse is true
            verify(spanMock).putTag(eq(KnownOpenTracingTags.ERROR), eq("true"));
        } else {
            // there shouldn't be any value for the error tag
            verify(spanMock, never()).putTag(eq(KnownOpenTracingTags.ERROR), any(String.class));
        }
    }

    @Test
    public void tagspanwithrequestattributes_behaves_as_expected() {
        // when
        openTracingTagStrategy.tagSpanWithRequestAttributes(spanMock, nullObj);

        // then
        verify(spanMock).putTag(eq(KnownOpenTracingTags.HTTP_METHOD), eq(httpMethod));
        verify(spanMock).putTag(eq(KnownOpenTracingTags.HTTP_URL), eq(httpUrl));
    }

    private HttpTagAdapter<Object,Object> tagAdapter = new HttpTagAdapter<Object,Object>() {

        @Override
        public boolean isErrorResponse(Object response) {
            return isErroredResponse;
        }

        @Override
        public String getResponseHttpStatus(Object response) {
            return responseStatus;
        }

        @Override
        public String getRequestHttpMethod(Object request) {
            return httpMethod;
        }

        @Override
        public String getRequestUrl(Object request) {
            return httpUrl;
        }

        @Override
        public String getRequestUri(Object request) {
            return null;
        }

    };

    private OpenTracingTagStrategy<Object,Object> openTracingTagStrategy = new OpenTracingTagStrategy<Object,Object>(tagAdapter);
}
