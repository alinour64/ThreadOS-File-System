/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 * 
 * The File Table class acts like a secretary for all the files currently in use. It keeps a detailed list, and each entry is like a 
 * file's "open" status report.  You need this class to make new entries (when a file is opened) and remove them (when closed). 
 * It also lets you check up on a file's status. Basically, this class is all about making sure multiple programs can use files 
 * without stepping on each other's toes.
 */


import java.util.Vector;

public class FileTable {
    // File Structure Table

    private Vector < FileTableEntry > table; // the entity of File Structure Table
    private Directory dir; // the root directory

    public FileTable(Directory directory) { // a default constructor
        table = new Vector < FileTableEntry > (); // instantiate a file table
        dir = directory; // instantiate the root directory
    }

    // you implement
    // Allocates a new file table entry for the specified filename and mode, potentially creating a new file.
    public synchronized FileTableEntry falloc(String fname, String mode) {
        short iNumber = -1;
        Inode inode = null;
        FileTableEntry entry = null;

        // Get inode number from directory
        iNumber = dir.namei(fname);

        // If file exists, retrieve its inode
        if (iNumber >= 0) {
            inode = new Inode(iNumber);
        } else if (!mode.equals("r")) { // If file doesn't exist and mode is not read, create new file
            iNumber = dir.ialloc(fname); // Allocate inode for new file
            if (iNumber >= 0) {
                inode = new Inode(iNumber); // Initialize inode for new file
            }
        }

        // If inode is successfully retrieved or created
        if (inode != null) {
            inode.count++;
            inode.toDisk(iNumber); // Write inode to disk

            // Create new file table entry and add it to the file table
            entry = new FileTableEntry(inode, iNumber, mode);
            table.addElement(entry);
        }

        return entry; // Return the new or found file table entry
    }



    public synchronized boolean ffree(FileTableEntry e) {
        // receive a file table entry
        // free the file table entry corresponding to this index
        if (table.removeElement(e) == true) { // find this file table entry
            e.inode.count--; // this entry no longer points to this inode
            switch (e.inode.flag) {
                case 1:
                    e.inode.flag = 0;
                    break;
                case 2:
                    e.inode.flag = 0;
                    break;
                case 4:
                    e.inode.flag = 3;
                    break;
                case 5:
                    e.inode.flag = 3;
                    break;
            }
            e.inode.toDisk(e.iNumber); // reflect this inode to disk
            e = null; // this file table entry is erased.
            notify();
            return true;
        } else
            return false;
    }

    public synchronized boolean fempty() {
        return table.isEmpty(); // return if table is empty
    }
}