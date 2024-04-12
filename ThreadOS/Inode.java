/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 * 
 * The Inode class functions as the file's ID card within the file system. It holds all the important details: how big the 
 * file is, how often it's used, what permissions it has, and where to find its data on the disk. This class is the key to 
 * grabbing data blocks whenever you need them, getting more disk space for the file, and handling those extra structures 
 * that really big files need.
 */



import java.nio.ByteBuffer;
public class Inode {
    public final static int iNodeSize = 32; // fixed to 32 bytes
    public final static int directSize = 11; // # direct pointers

    public final static int NoError = 0;
    public final static int ErrorBlockRegistered = -1;
    public final static int ErrorPrecBlockUnused = -2;
    public final static int ErrorIndirectNull = -3;

    public int length; // file size in bytes
    public short count; // # file-table entries pointing to this
    public short flag; // 0 = unused, 1 = used(r), 2 = used(!r), 
    // 3=unused(wreg), 4=used(r,wreq), 5= used(!r,wreg)
    public short direct[] = new short[directSize]; // directo pointers
    public short indirect; // an indirect pointer

    Inode() { // a default constructor
        length = 0;
        count = 0;
        flag = 1;
        for (int i = 0; i < directSize; i++)
            direct[i] = -1;
        indirect = -1;
    }

    // making inode from disk
    Inode(short iNumber) {
        int blkNumber = 1 + iNumber / 16; // inodes start from block#1
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blkNumber, data); // get the inode block
        int offset = (iNumber % 16) * iNodeSize; // locate the inode top

        length = SysLib.bytes2int(data, offset); // retrieve all data members
        offset += 4; // from data
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;
        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(data, offset);
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
        offset += 2;

        /*
        System.out.println( "Inode[" + iNumber + "]: retrieved " +
        			" length = " + length +
        			" count = " + count +
        			" flag = " + flag +
        			" direct[0] = " + direct[0] +
        			" indirect = " + indirect );
        */
    }
    // you implement
    // Serializes and writes this inode to disk based on the provided inode number.
    public void toDisk(short iNumber) {
        // Allocate a ByteBuffer for inode serialization
        ByteBuffer buffer = ByteBuffer.allocate(iNodeSize);
        // Populate the buffer with inode data: length, count, flag, direct pointers, and indirect pointer
        buffer.putInt(length)
            .putShort(count)
            .putShort(flag);
        for (int i = 0; i < directSize; i++) {
            buffer.putShort(direct[i]);
        }
        buffer.putShort(indirect);
        buffer.flip(); // Prepare buffer for reading

        byte[] inodeBlock = new byte[Disk.blockSize];
        int blkNumber = 1 + iNumber / 16; // Calculate block number where the inode should be stored
        SysLib.rawread(blkNumber, inodeBlock);

        int blockOffset = (iNumber % 16) * iNodeSize; // Calculate the offset within the block for this inode
        System.arraycopy(buffer.array(), 0, inodeBlock, blockOffset, iNodeSize);
        SysLib.rawwrite(blkNumber, inodeBlock);
    }


    // you implement
    // Determines the block number that contains the data at the specified offset within the file.
    public int findTargetBlock(int offset) {
        int block = offset / Disk.blockSize;

        if (block < directSize) {
            return direct[block];
        }

        if (indirect < 0) {
            return -1;
        }

        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);

        // Adjust block index to be relative to the start of the indirect pointers
        block -= directSize;

        // Check if the adjusted block index is within the range a single block can index
        if (block < Disk.blockSize / 2) {
            // Convert 2 bytes from the buffer into a short to get the block number
            return SysLib.bytes2short(data, block * 2);
        }

        // If block index exceeds the capacity of the indirect block, return error
        return -1;
    }


    // you implement

    // Registers a new target block at the specified offset, ensuring correct allocation in both direct and indirect cases.
    public boolean registerTargetBlock(int offset, int blockNumber) {
        int blockIndex = offset / Disk.blockSize;

        if (blockIndex < directSize) {
            // If the block is already allocated or the previous block is unallocated, can't proceed
            if (direct[blockIndex] >= 0 || (blockIndex > 0 && direct[blockIndex - 1] == -1)) {
                return false;
            }
            // Allocate the block directly
            direct[blockIndex] = (short) blockNumber;
            return true;
        }

        if (indirect < 0) {
            // If no indirect block exists, this operation is not valid as it implies allocating non-contiguous space
            return false;
        }

        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(indirect, data);

        // Calculate the index within the indirect block
        blockIndex -= directSize;
        if (blockIndex < Disk.blockSize / 2) { // Ensure the block index is within valid range
            // Write the block number into the indirect block
            SysLib.short2bytes((short) blockNumber, data, blockIndex * 2);
            SysLib.rawwrite(indirect, data);
            return true;
        }

        // If the block index is out of range, the operation fails
        return false;
    }

    // Frees and returns the data of the indirect block if it exists, marking it as unallocated.
    public byte[] freeIndirectBlocks() {
        if (indirect >= 0) {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);
            indirect = -1; // Mark the indirect block as free
            return data; // Return the data from the freed indirect block
        }
        return null; // If no indirect block was allocated, return null
    }

}