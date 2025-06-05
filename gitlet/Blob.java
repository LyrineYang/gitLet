package gitlet;

import java.io.Serializable;
/**
 *  @author Lyrine Yang
 */

public class Blob implements Serializable {
    private byte[] content;
    public Blob(byte[] byteContent) {
        content = byteContent;
    }

    public byte[] getContent() {
        return content;
    }
}
