package com.czertainly.core.config;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;

public class CachedBodyServletOutputStream extends ServletOutputStream {

    public OutputStream outputStream;
    private CustomHttpServletResponseWrapper wrappedResponse;
    private ServletOutputStream servletOutputStream = new ServletOutputStream(){
        boolean isReady = true;
        WriteListener writeListener = null;

        @Override
        public void setWriteListener(WriteListener writeListener) {
            this.writeListener = writeListener;
        }

        public boolean isReady(){
            return isReady;
        }
        @Override
        public void write(int w) throws IOException {
            outputStream.write(w);
            wrappedResponse.rawData.add(Integer.valueOf(w).byteValue());
        }
    };

    public CachedBodyServletOutputStream(CustomHttpServletResponseWrapper wrappedResponse) throws IOException {
        this.outputStream = wrappedResponse.response.getOutputStream();
        this.wrappedResponse = wrappedResponse;
    }


    @Override
    public void setWriteListener(WriteListener writeListener) {
        servletOutputStream.setWriteListener( writeListener );
    }
    @Override
    public boolean isReady(){
        return servletOutputStream.isReady();
    }

    @Override
    public void write(int w) throws IOException {
        servletOutputStream.write(w);
    }
}
