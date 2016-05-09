package com.nike.wingtips.servlet;

import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests the functionality of {@link RequestTracingFilterNoAsync}
 */
public class RequestTracingFilterNoAsyncTest {

    private RequestTracingFilterNoAsync instance = new RequestTracingFilterNoAsync();

    @Test
    public void isAsyncDispatch_should_always_return_false() {
        assertThat(instance.isAsyncDispatch(mock(HttpServletRequest.class)), is(false));
    }

}
