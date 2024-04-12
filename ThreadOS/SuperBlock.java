/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 * 
 * The Superblock class holds the blueprints for the entire file system. Inside, you'll find stuff like how many blocks the disk has, 
 * which blocks are set aside for special purposes, and where the list of available blocks lives.  It's the toolbox for setting up the 
 * superblock in the first place, formatting the disk, keeping any changes saved, grabbing free blocks when you need them, and putting 
 * blocks back on the "available" list.
 */


class SuperBlock {
    private final int defaulttotalInodes = 64;
    public int totalBlocks;
    public int inodeBlocks;
    public int freeList;

    // you implement
    // Initializes the superblock with the filesystem's structural information from disk or formats if invalid.
    public SuperBlock(int diskSize) {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.rawread(0, superBlock);
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        inodeBlocks = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        // Validate the filesystem structure. If invalid, reformat with default settings
        if (totalBlocks != diskSize || inodeBlocks <= 0 || freeList < 2) {
            // Reinitialize variables to default and format if there's a discrepancy
            totalBlocks = diskSize; // Update totalBlocks to match disk size
            format(defaulttotalInodes); // Format the disk with a default number of inodes
        }
    }


    //  helper function
    void sync() {
        byte[] superBlock = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, superBlock, 0);
        SysLib.int2bytes(inodeBlocks, superBlock, 4);
        SysLib.int2bytes(freeList, superBlock, 8);
        SysLib.rawwrite(0, superBlock);
        SysLib.cerr("Superblock synchronized\n");
    }

    void format() {
        // default format with 64 inodes
        format(defaulttotalInodes);
    }

    // you implement
    // Formats the disk with a specified number of files, initializing inodes and setting up the free list.
    void format(int files) {
        // Set the number of inodes based on the number of files
        inodeBlocks = files;
        // Initialize each inode and save it to disk
        for (int i = 0; i < inodeBlocks; i++) {
            Inode inode = new Inode(); // Create a new inode
            inode.flag = 0; // Reset flag to indicate inode is unused
            inode.toDisk((short) i); // Write inode to disk
        }

        // Calculate the starting index of the free list
        freeList = 2 + inodeBlocks * 32 / Disk.blockSize; // Assuming each inode takes 32 bytes
        // Initialize the free block list on disk
        for (int i = freeList; i < totalBlocks; i++) {
            byte[] block = new byte[Disk.blockSize]; // Create a block-sized byte array
            // Point each block to the next, or mark the end with -1
            SysLib.int2bytes(i + 1 < totalBlocks ? i + 1 : -1, block, 0);
            SysLib.rawwrite(i, block); // Write the block to disk
        }
        sync(); // Save the updated superblock to disk
    }
    //implement
    // Retrieves and removes the first free block from the free list, updating the free list.
    public int getFreeBlock() {
        if (freeList == -1) {
            return -1;
        }
        int block = freeList;
        byte[] buffer = new byte[Disk.blockSize];
        SysLib.rawread(block, buffer); // Read the block from disk
        freeList = SysLib.bytes2int(buffer, 0); // Update the free list to the next block
        return block; // Return the block number of the retrieved free block
    }

    //implement
    // Adds a block back to the free list, marking it as available for use.
    public boolean returnBlock(int blockNumber) {
        if (blockNumber < 0) {
            return false; // Invalid block numbers cannot be returned
        }
        byte[] buffer = new byte[Disk.blockSize];
        SysLib.int2bytes(freeList, buffer, 0); // Write the current head of the free list into the buffer
        SysLib.rawwrite(blockNumber, buffer); // Write the buffer to the block, effectively adding it to the free list
        freeList = blockNumber; // Update the head of the free list to this block
        return true;
    }

}