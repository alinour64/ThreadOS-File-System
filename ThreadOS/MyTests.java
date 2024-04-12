class MyTests extends Thread {
    private final static int DEFAULTFILES = 48;
    private final int files;
    private int fd;
    private final byte[] buf16 = new byte[16];
    private final byte[] buf32 = new byte[32];
    private final byte[] buf48 = new byte[48];
    private int size;

    public MyTests(String args[]) {
        files = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULTFILES;
        for (byte i = 0; i < 16; i++) buf16[i] = i;
        for (byte i = 0; i < 32; i++) buf32[i] = i;
        for (byte i = 0; i < 48; i++) buf48[i] = i;
    }

    public MyTests() {
        this(new String[]{String.valueOf(DEFAULTFILES)});
    }

    public void run() {
        if (formatTest()) 
            SysLib.cout("Format test: Success\n");
        if (openWriteCloseReadTest()) 
            SysLib.cout("Open-Write-Close-Read test: Success\n");
        if (appendReadTest()) 
            SysLib.cout("Append-Read test: Success\n");
        if (seekReadTest()) 
            SysLib.cout("Seek-Read test: Success\n");
        if (deleteTest()) 
            SysLib.cout("Delete test: Success\n");
        if (multipleWritesReadsTest())
            SysLib.cout("Multiple Writes-Reads test: Success\n");
        if (overwriteTest())
            SysLib.cout("Overwrite test: Success\n");
        if (concurrentAccessTest())
            SysLib.cout("Concurrent File Access test: Success\n");
        if (largeFileTest())
            SysLib.cout("Large File test: Success\n");
        
        SysLib.cout("All tests completed\n");
        SysLib.exit();
    }

    private boolean formatTest() {
        SysLib.format(files);
        SysLib.cout("Disk formatted.\n");
        return true;
    }

    private boolean openWriteCloseReadTest() {
        String fileName = "testFile1";
        fd = SysLib.open(fileName, "w+");
        if (fd == -1) {
            SysLib.cout("Failed to open file for writing.\n");
            return false;
        }
        SysLib.write(fd, buf16);
        SysLib.close(fd);

        fd = SysLib.open(fileName, "r");
        byte[] readBuffer = new byte[16];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (buf16[i] != readBuffer[i]) {
                SysLib.cout("Read data does not match written data.\n");
                return false;
            }
        }
        SysLib.cout("Write and read test passed.\n");
        return true;
    }

    private boolean appendReadTest() {
        String fileName = "testFile2";
        fd = SysLib.open(fileName, "a");
        SysLib.write(fd, buf32);
        SysLib.close(fd);

        fd = SysLib.open(fileName, "r");
        byte[] readBuffer = new byte[32];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (buf32[i] != readBuffer[i]) {
                SysLib.cout("Append data does not match.\n");
                return false;
            }
        }
        SysLib.cout("Append and read test passed.\n");
        return true;
    }

    private boolean seekReadTest() {
        String fileName = "testFile3";
        fd = SysLib.open(fileName, "w+");
        SysLib.write(fd, buf48);
        SysLib.seek(fd, 16, 0);
        byte[] readBuffer = new byte[32];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (buf48[i+16] != readBuffer[i]) {
                SysLib.cout("Seek read data does not match.\n");
                return false;
            }
        }
        SysLib.cout("Seek and read test passed.\n");
        return true;
    }

    private boolean deleteTest() {
        String fileName = "testFile1";
        SysLib.delete(fileName);
        fd = SysLib.open(fileName, "r");
        if (fd != -1) {
            SysLib.cout("File was not deleted properly.\n");
            return false;
        }
        SysLib.cout("Delete test passed.\n");
        return true;
    }
        private boolean multipleWritesReadsTest() {
        String fileName = "multiWriteReadTestFile";
        fd = SysLib.open(fileName, "w+");
        for (int i = 0; i < 3; i++) {
            SysLib.write(fd, buf16);
        }
        SysLib.seek(fd, 0, 0); 
        byte[] readBuffer = new byte[48]; 
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (readBuffer[i] != buf16[i % 16]) {
                SysLib.cout("Multiple writes-reads data mismatch.\n");
                return false;
            }
        }
        SysLib.cout("Multiple writes-reads test passed.\n");
        return true;
    }

    private boolean overwriteTest() {
        String fileName = "overwriteTestFile";
        fd = SysLib.open(fileName, "w+");
        SysLib.write(fd, buf16);
        SysLib.seek(fd, 0, 0); 
        SysLib.write(fd, buf32); 
        SysLib.seek(fd, 0, 0); 
        byte[] readBuffer = new byte[32];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (readBuffer[i] != buf32[i]) {
                SysLib.cout("Overwrite test data mismatch.\n");
                return false;
            }
        }
        SysLib.cout("Overwrite test passed.\n");
        return true;
    }

    private boolean concurrentAccessTest() {
        String fileName = "concurrentAccessTestFile";
        fd = SysLib.open(fileName, "w+");
        SysLib.write(fd, buf16);
        int fd2 = SysLib.open(fileName, "w");
        SysLib.write(fd2, buf32); 
        SysLib.close(fd2);
        SysLib.seek(fd, 0, 0);
        byte[] readBuffer = new byte[32];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (readBuffer[i] != buf32[i]) {
                SysLib.cout("Concurrent access test data mismatch.\n");
                return false;
            }
        }
        SysLib.cout("Concurrent access test passed.\n");
        return true;
    }

    private boolean largeFileTest() {
        String fileName = "largeFileTestFile";
        fd = SysLib.open(fileName, "w+");
        byte[] largeBuf = new byte[1024 * 2]; 
        for (int i = 0; i < largeBuf.length; i++) {
            largeBuf[i] = (byte)(i % 256);
        }
        SysLib.write(fd, largeBuf);
        SysLib.seek(fd, 0, 0);
        byte[] readBuffer = new byte[largeBuf.length];
        size = SysLib.read(fd, readBuffer);
        SysLib.close(fd);
        for (int i = 0; i < size; i++) {
            if (readBuffer[i] != largeBuf[i]) {
                SysLib.cout("Large file test data mismatch.\n");
                return false;
            }
        }
        SysLib.cout("Large file test passed.\n");
        return true;
    }


}
