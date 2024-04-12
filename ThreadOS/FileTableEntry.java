/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 * 
 * The File Table Entry class is like a file's progress report. Each entry is a little snapshot of where things stand: 
 * where you are in the file (the seek pointer), the file's ID number (the inode stuff), how many programs are using the 
 * file, and whether they have permission to read, write, or both. Every time you open a file, one of these gets created to 
 * keep track of everything.
 */

public class FileTableEntry {
    public int seekPtr; //    a file seek pointer
    public final Inode inode; //    a reference to an inode
    public final short iNumber; //    this inode number
    public int count; //    a count to maintain #threads sharing this
    public final String mode; //    "r", "w", "w+", or "a"
    FileTableEntry(Inode i, short inumber, String m) {
        seekPtr = 0; // the seek pointer is set to the file top.
        inode = i;
        iNumber = inumber;
        count = 1; // at least one thread is using this entry.
        mode = m; // once file access mode is set, it never changes.

        if (mode.compareTo("a") == 0)
            seekPtr = inode.length;
    }
}