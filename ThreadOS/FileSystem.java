/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 *
 * The File System class is the master control for your operating system's file system. It handles the big-picture 
 * organization (like the superblock and directories) and the details of individual files through the file table.  
 * It has the tools to format your disk, open, close, read, write, and delete files, and it keeps everything synced 
 * between the disk and memory. You can even use it to jump around within a file using that seek pointer.
 */


public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

    public FileSystem(int diskBlocks) {
        // create superblock, and format disk with 64 inodes in default
        superblock = new SuperBlock(diskBlocks);
        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);
        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        // directory reconstruction
        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    void sync() {
        // directory synchronizatioin
        FileTableEntry dirEnt = open("/", "w");
        byte[] dirData = directory.directory2bytes();
        write(dirEnt, dirData);
        close(dirEnt);
        // superblock synchronization
        superblock.sync();
    }

    boolean format(int files) {
        // wait until all filetable entries are destructed
        while (filetable.fempty() == false);

        // format superblock, initialize inodes, and create a free list
        superblock.format(files);

        // create directory, and register "/" in directory entry 0
        directory = new Directory(superblock.inodeBlocks);

        // file table is created, and store directory in the file table
        filetable = new FileTable(directory);

        return true;
    }

    //implement
    // Opens a file given its name and the mode ('r', 'w', 'w+', 'a'). Returns a FileTableEntry.
    // If opening for read ('r') and the file doesn't exist, it returns null. 'a' mode sets the seek pointer to the file's end.
    FileTableEntry open(String filename, String mode) {
        boolean fileExists = directory.namei(filename) >= 0; // Check if file exists

        if ("r".equals(mode) && !fileExists) { // For read mode, return null if file doesn't exist
            return null;
        }

        FileTableEntry ftEnt = filetable.falloc(filename, mode); // Allocate a new file table entry

        if ("a".equals(mode) && ftEnt != null) { // If mode is append, set seekPtr to file's end
            ftEnt.seekPtr = ftEnt.inode.length;
        }

        return ftEnt; // Return the file table entry
    }


    boolean close(FileTableEntry ftEnt) {
        // filetable entry is freed
        synchronized(ftEnt) {
            // need to decrement count; also: changing > 1 to > 0 below
            ftEnt.count--;
            if (ftEnt.count > 0) // my children or parent are(is) using it
                return true;
        }
        return filetable.ffree(ftEnt);
    }
    //implement
    // Returns the size of the file associated with the provided FileTableEntry.
    // It synchronizes on the FileTableEntry object to ensure thread-safe access.
    public int fsize(FileTableEntry ftEnt) {
        synchronized(ftEnt) { // Lock on ftEnt for thread safety
            return ftEnt.inode.length; // Return file length
        }
    }

    //implement
    // Reads data from a file into a buffer array, starting from the file's current seek pointer.
    // Returns the number of bytes read or -1 if the file is open in write-only or append mode.
    int read(FileTableEntry ftEnt, byte[] buffer) {
        if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a")) return -1; // Can't read in write-only or append mode

        int bufferSize = buffer.length;
        int bytesRead = 0;
        int error = -1;
        int blockSize = Disk.blockSize;
        int readSize;

        synchronized(ftEnt) { // Ensure thread-safe access to the file table entry
            while (ftEnt.seekPtr < fsize(ftEnt) && bufferSize > 0) {
                int blockNumber = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (blockNumber == error) break; // Exit if can't find the block

                byte[] blockData = new byte[blockSize];
                SysLib.rawread(blockNumber, blockData);
                // Calculate offsets and sizes for copying data
                int dataOffset = ftEnt.seekPtr % blockSize;
                int blockBytesLeft = blockSize - dataOffset;
                int fileBytesLeft = fsize(ftEnt) - ftEnt.seekPtr;
                readSize = Math.min(blockBytesLeft, Math.min(fileBytesLeft, bufferSize));
                // Copy from blockData to buffer
                System.arraycopy(blockData, dataOffset, buffer, bytesRead, readSize);
                // Update pointers and counters
                ftEnt.seekPtr += readSize;
                bytesRead += readSize;
                bufferSize -= readSize;
            }
        }

        return bytesRead; // Return the total number of bytes read
    }

    //implement
    // Writes data from a buffer into the file associated with the provided FileTableEntry.
    // Handles file expansion and updates the file's seek pointer and length as necessary.
    int write(FileTableEntry ftEnt, byte[] buffer) {
        // Check for invalid entry or buffer
        if (ftEnt == null || buffer == null) return -1;
        // Ensure file is open in a writable mode
        if (!ftEnt.mode.equals("w") && !ftEnt.mode.equals("w+") && !ftEnt.mode.equals("a")) return -1;

        synchronized(ftEnt) { // Synchronize on the file table entry for thread safety
            int bytesWritten = 0;
            int bufferSize = buffer.length;

            while (bufferSize > bytesWritten) {
                // Find the target block for the current seek pointer
                int blockNumber = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
                if (blockNumber == -1) { // If no block found, allocate a new one
                    short newBlock = findFreeBlock();
                    // Attempt to register the new block
                    boolean success = newBlock != -1 && registerTargetBlock(ftEnt, ftEnt.seekPtr, newBlock);
                    if (!success) {
                        break; // Exit if block allocation or registration fails
                    }
                    blockNumber = newBlock;
                }

                byte[] blockData = new byte[Disk.blockSize];
                SysLib.rawread(blockNumber, blockData); // Read existing block data

                // Calculate offset and size for the write operation
                int dataOffset = ftEnt.seekPtr % Disk.blockSize;
                int spaceLeft = Disk.blockSize - dataOffset;
                int writeSize = Math.min(spaceLeft, bufferSize - bytesWritten);

                // Write buffer data to block data
                System.arraycopy(buffer, bytesWritten, blockData, dataOffset, writeSize);
                SysLib.rawwrite(blockNumber, blockData); // Write the block back to disk

                // Update pointers and counters
                ftEnt.seekPtr += writeSize;
                bytesWritten += writeSize;
            }

            // Update file length if it has grown
            if (ftEnt.seekPtr > ftEnt.inode.length) {
                ftEnt.inode.length = ftEnt.seekPtr; // Update inode length
                ftEnt.inode.toDisk(ftEnt.iNumber); // Save the inode to disk
            }

            return bytesWritten; // Return the number of bytes written
        }
    }

    //implement
    // Retrieves a free disk block from the superblock. Returns the block number or -1 if none are available.
    private short findFreeBlock() {
        int freeBlock = superblock.getFreeBlock(); // Attempt to get a free block from the superblock
        if (freeBlock == -1) {
            return -1; // Return -1 if no free block is available
        } else {
            return (short) freeBlock; // Convert to short and return the free block number
        }
    }

    //implement
    // Allocates and registers a target block for an inode based on the seek pointer. Handles direct and indirect blocks.
    // Returns true if successful, false otherwise (e.g., block already allocated or failure to allocate indirect block).
    private boolean registerTargetBlock(FileTableEntry ftEnt, int seekPtr, short blockNumber) {
        int blockIndex = seekPtr / Disk.blockSize; // Calculate block index from seek pointer

        if (blockIndex < Inode.directSize) { // Direct block allocation
            if (ftEnt.inode.direct[blockIndex] != -1) return false; // Block already allocated
            ftEnt.inode.direct[blockIndex] = blockNumber; // Register direct block
        } else { // Indirect block allocation
            if (ftEnt.inode.indirect == -1) { // No indirect block, need allocation
                short indirectBlock = this.findFreeBlock();
                if (indirectBlock == -1) return false;

                ftEnt.inode.indirect = indirectBlock;
                byte[] data = new byte[Disk.blockSize];
                for (int i = 0; i < data.length; i += 2) {
                    SysLib.short2bytes((short) - 1, data, i); // Initialize indirect block pointers
                }
                SysLib.rawwrite(indirectBlock, data); // Initialize indirect block on disk
            }

            byte[] indirectData = new byte[Disk.blockSize];
            SysLib.rawread(ftEnt.inode.indirect, indirectData); // Read current indirect block

            int indirectIndex = blockIndex - Inode.directSize; // Calculate index within the indirect block
            if (SysLib.bytes2short(indirectData, indirectIndex * 2) != -1) return false; // Check if target block is already allocated

            SysLib.short2bytes(blockNumber, indirectData, indirectIndex * 2); // Register new block in indirect block
            SysLib.rawwrite(ftEnt.inode.indirect, indirectData); // Update indirect block on disk
        }

        return true;
    }

    //implement
    private boolean deallocAllBlocks(FileTableEntry ftEnt) {
        if (ftEnt == null || ftEnt.inode == null) {
            return false;
        }

        // Deallocate all direct blocks
        for (int i = 0; i < ftEnt.inode.direct.length; i++) {
            if (ftEnt.inode.direct[i] != -1) { // Check if block is allocated
                superblock.returnBlock(ftEnt.inode.direct[i]); // Return block to superblock
                ftEnt.inode.direct[i] = -1; // Mark block as deallocated
            }
        }

        // Deallocate indirect blocks, if any
        if (ftEnt.inode.indirect != -1) {
            byte[] dataBuffer = new byte[Disk.blockSize]; // Buffer to read indirect block data
            SysLib.rawread(ftEnt.inode.indirect, dataBuffer); // Read indirect block

            int offset = 0;
            short block = SysLib.bytes2short(dataBuffer, offset);
            while (block != -1 && offset < Disk.blockSize) {
                superblock.returnBlock(block); // Return block to superblock
                offset += 2;
                block = SysLib.bytes2short(dataBuffer, offset); // Read next block number
            }

            superblock.returnBlock(ftEnt.inode.indirect); // Return the indirect block itself
            ftEnt.inode.indirect = -1; // Mark indirect block as deallocated
        }

        ftEnt.inode.toDisk(ftEnt.iNumber); // Save inode changes to disk

        return true;
    }

    boolean delete(String filename) {
        FileTableEntry ftEnt = open(filename, "w");
        short iNumber = ftEnt.iNumber;
        return close(ftEnt) && directory.ifree(iNumber);
    }

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;
    //implement
    // Adjusts the file's seek pointer based on the offset and whence parameters.
    int seek(FileTableEntry fileEntry, int offset, int whence) {
        synchronized(fileEntry) {
            int fileSize = this.fsize(fileEntry);
            switch (whence) {
                case 0:
                    // Set seek pointer ensuring it's within the file bounds
                    fileEntry.seekPtr = Math.max(0, Math.min(offset, fileSize));
                    break;
                case 1:
                    // Calculate new position from current and adjust within file bounds
                    int newPosFromCurrent = fileEntry.seekPtr + offset;
                    fileEntry.seekPtr = Math.max(0, Math.min(newPosFromCurrent, fileSize));
                    break;
                case 2:
                    int newPosFromEnd = fileSize + offset;
                    fileEntry.seekPtr = Math.max(0, Math.min(newPosFromEnd, fileSize));
                    break;
            }
            return fileEntry.seekPtr; // Return the updated seek pointer position
        }
    }

}