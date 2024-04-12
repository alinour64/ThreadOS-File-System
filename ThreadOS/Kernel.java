/*
 * Names (Group C): Nour Ali, Ibrahim Deria
 * Professor: Erika Parsons
 * Class: CSS430
 * Assignment: Final Project - File System
 * Description:
 * 
 * The Kernel class functions as the operating system's engine room.  It handles all the behind-the-scenes work:  
 * routing requests from programs, juggling processes and threads, managing disk operations, keeping frequently used data 
 * handy, and making sure the file system plays nicely with the rest of the system. It's the foundation everything else is built on.
 */

import java.io.BufferedReader;
import java.util.HashMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Kernel {
    // Interrupt requests
    public final static int INTERRUPT_SOFTWARE = 1; // System calls
    public final static int INTERRUPT_DISK = 2; // Disk interrupts
    public final static int INTERRUPT_IO = 3; // Other I/O interrupts

    // System calls
    public final static int BOOT = 0; // SysLib.boot( )
    public final static int EXEC = 1; // SysLib.exec(String args[])
    public final static int WAIT = 2; // SysLib.join( )
    public final static int EXIT = 3; // SysLib.exit( )
    public final static int SLEEP = 4; // SysLib.sleep(int milliseconds)
    public final static int RAWREAD = 5; // SysLib.rawread(int blk, byte b[])
    public final static int RAWWRITE = 6; // SysLib.rawwrite(int blk, byte b[])
    public final static int SYNC = 7; // SysLib.sync( )
    public final static int READ = 8; // SysLib.cin( )
    public final static int WRITE = 9; // SysLib.cout( ) and SysLib.cerr( )

    // System calls to be added in Assignment 4
    public final static int CREAD = 10; // SysLib.cread(int blk, byte b[])
    public final static int CWRITE = 11; // SysLib.cwrite(int blk, byte b[])
    public final static int CSYNC = 12; // SysLib.csync( )
    public final static int CFLUSH = 13; // SysLib.cflush( )

    // System calls to be added in Project
    public final static int OPEN = 14; // SysLib.open( String fileName )
    public final static int CLOSE = 15; // SysLib.close( int fd )
    public final static int SIZE = 16; // SysLib.size( int fd )
    public final static int SEEK = 17; // SysLib.seek( int fd, int offest,
    //              int whence )
    public final static int FORMAT = 18; // SysLib.format( int files )
    public final static int DELETE = 19; // SysLib.delete( String fileName )

    // Predefined file descriptors
    public final static int STDIN = 0;
    public final static int STDOUT = 1;
    public final static int STDERR = 2;

    // Return values
    public final static int OK = 0;
    public final static int ERROR = -1;

    // System thread references
    private static Scheduler scheduler;
    private static Disk disk;
    private static Cache cache;

    // Synchronized Queues
    private static SyncQueue waitQueue; // for threads to wait for their child
    private static SyncQueue ioQueue; // I/O queue

    private final static int COND_DISK_REQ = 1; // wait condition
    private final static int COND_DISK_FIN = 2; // wait condition
    // System thread references
    private static FileSystem fileSystem;
    // Standard input
    private static BufferedReader input
        = new BufferedReader(new InputStreamReader(System.in));

    private static HashMap < Integer, FileTableEntry > fdMap = new HashMap < > ();
    private static int nextFd = 0; // Simple counter to generate new file descriptors

    // The heart of Kernel
    public static int interrupt(int irq, int cmd, int param, Object args) {
        TCB myTcb;
        switch (irq) {
            case INTERRUPT_SOFTWARE: // System calls
                switch (cmd) {
                    case BOOT:
                        // instantiate and start a scheduler
                        scheduler = new Scheduler();
                        scheduler.start();

                        // instantiate and start a disk
                        disk = new Disk(1000);
                        disk.start();

                        // instantiate a cache memory
                        cache = new Cache(disk.blockSize, 10);

                        // instantiate synchronized queues
                        ioQueue = new SyncQueue();
                        waitQueue = new SyncQueue(scheduler.getMaxThreads());
                        fileSystem = new FileSystem(1000);
                        return OK;
                    case EXEC:
                        return sysExec((String[]) args);
                    case WAIT:
                        if ((myTcb = scheduler.getMyTcb()) != null) {
                            int myTid = myTcb.getTid(); // get my thread ID
                            return waitQueue.enqueueAndSleep(myTid); //wait on my tid
                            // woken up by my child thread
                        }
                        return ERROR;
                    case EXIT:
                        if ((myTcb = scheduler.getMyTcb()) != null) {
                            int myPid = myTcb.getPid(); // get my parent ID
                            int myTid = myTcb.getTid(); // get my ID
                            if (myPid != -1) {
                                // wake up a thread waiting on my parent ID
                                waitQueue.dequeueAndWakeup(myPid, myTid);
                                // I'm terminated!
                                scheduler.deleteThread();
                                return OK;
                            }
                        }
                        return ERROR;
                    case SLEEP: // sleep a given period of milliseconds
                        scheduler.sleepThread(param); // param = milliseconds
                        return OK;
                    case RAWREAD: // read a block of data from disk
                        while (disk.read(param, (byte[]) args) == false)
                            ioQueue.enqueueAndSleep(COND_DISK_REQ);
                        while (disk.testAndResetReady() == false)
                            ioQueue.enqueueAndSleep(COND_DISK_FIN);

                        // it's possible that a thread waiting to make a request was released by the disk,
                        // but then promptly looped back, found the buffer wasn't available for sending (bufferReady == true)
                        // and then went back to sleep

                        // now you can access data in buffer
                        return OK;
                    case RAWWRITE: // write a block of data to disk
                        while (disk.write(param, (byte[]) args) == false)
                            ioQueue.enqueueAndSleep(COND_DISK_REQ);
                        while (disk.testAndResetReady() == false)
                            ioQueue.enqueueAndSleep(COND_DISK_FIN);
                        // it's possible that a thread waiting to make a request was released by the disk,
                        // but then promptly looped back, found the buffer wasn't available for sending (bufferReady == true)
                        // and then went back to sleep

                        return OK;
                    case SYNC: // synchronize disk data to a real file
                        while (disk.sync() == false)
                            ioQueue.enqueueAndSleep(COND_DISK_REQ);
                        while (disk.testAndResetReady() == false)
                            ioQueue.enqueueAndSleep(COND_DISK_FIN);

                        // it's possible that a thread waiting to make a request was released by the disk,
                        // but then promptly looped back, found the buffer wasn't available for sending (bufferReady == true)
                        // and then went back to sleep

                        return OK;
                    case READ:
                        switch (param) {
                            case STDIN:
                                try {
                                    String s = input.readLine(); // read a keyboard input
                                    if (s == null) {
                                        return ERROR;
                                    }
                                    // prepare a read buffer
                                    StringBuffer buf = (StringBuffer) args;

                                    // append the keyboard intput to this read buffer
                                    buf.append(s);

                                    // return the number of chars read from keyboard
                                    return s.length();
                                } catch (IOException e) {
                                    System.out.println(e);
                                    return ERROR;
                                }
                            case STDOUT:
                            case STDERR:
                                System.out.println("threadOS: caused read errors");
                                return ERROR;
                        }
                        myTcb = scheduler.getMyTcb();
                        int result;

                        if (myTcb != null) {
                            FileTableEntry fileEntry = myTcb.getFtEnt(param);
                            if (fileEntry != null) {
                                byte[] data = (byte[]) args;
                                result = fileSystem.read(fileEntry, data);
                            } else {
                                result = ERROR;
                            }
                        } else {
                            result = ERROR;
                        }

                        return result;
                        // return FileSystem.read( param, byte args[] );
                    case WRITE:
                        switch (param) {
                            case STDIN:
                                System.out.println("threadOS: cannot write to System.in");
                                return ERROR;
                            case STDOUT:
                                System.out.print((String) args);
                                return OK;
                            case STDERR:
                                System.err.print((String) args);
                                return OK;
                        }
                        myTcb = scheduler.getMyTcb();

                        if (myTcb != null) {
                            FileTableEntry fileEntry = myTcb.getFtEnt(param);
                            if (fileEntry != null) {
                                byte[] data = (byte[]) args;
                                result = fileSystem.write(fileEntry, data);
                            } else {
                                result = OK;
                            }
                        } else {
                            result = OK;
                        }

                        return result;
                    case CREAD:
                        return cache.read(param, (byte[]) args) ? OK : ERROR;
                    case CWRITE:
                        return cache.write(param, (byte[]) args) ? OK : ERROR;
                    case CSYNC:
                        cache.sync();
                        return OK;
                    case CFLUSH:
                        cache.flush();
                        return OK;
                    case OPEN:
                        // Attempt to open a file
                        myTcb = scheduler.getMyTcb();
                        if (myTcb != null) {
                            String[] arguments = (String[]) args;
                            if (arguments.length >= 2) {
                                String filename = arguments[0];
                                String mode = arguments[1];
                                // Open the file with the specified filename and mode
                                FileTableEntry fte = fileSystem.open(filename, mode);
                                if (fte != null) {
                                    // If successful, obtain the file descriptor and return it
                                    int fd = myTcb.getFd(fte);
                                    return fd;
                                } else {
                                    // If opening failed, return error
                                    return ERROR;
                                }
                            } else {
                                // Insufficient arguments provided, return error
                                return ERROR;
                            }
                        } else {
                            // If no thread control block exists, return error
                            return ERROR;
                        }

                    case CLOSE:
                        // Attempt to close a file
                        myTcb = scheduler.getMyTcb();
                        if (myTcb != null) {
                            // Retrieve the file table entry associated with the file descriptor
                            FileTableEntry ftEnt = myTcb.getFtEnt(param);
                            // Check if the file can be closed successfully
                            boolean shouldReturnError = ftEnt == null || !fileSystem.close(ftEnt);
                            if (shouldReturnError || myTcb.returnFd(param) != ftEnt) {
                                // If closing failed, or the file descriptor is not valid, return error
                                return ERROR;
                            } else {
                                // Otherwise, return success
                                return OK;
                            }
                        } else {
                            // If no thread control block exists, return error
                            return ERROR;
                        }

                    case SIZE:
                        // Retrieve the size of a file
                        {
                            // Retrieve the file table entry associated with the file descriptor
                            FileTableEntry ftEnt = fdMap.get(param);
                            // If the file table entry doesn't exist, return error
                            if (ftEnt == null) return ERROR;
                            // Otherwise, return the size of the file
                            int size = fileSystem.fsize(ftEnt);
                            return size;
                        }

                    case SEEK:
                        // Perform a seek operation within a file
                        myTcb = scheduler.getMyTcb();
                        if (myTcb != null) {
                            int[] find = (int[]) args;
                            // Retrieve the file table entry associated with the file descriptor
                            FileTableEntry ftEnt = myTcb.getFtEnt(param);
                            if (ftEnt != null) {
                                // If the file table entry exists, perform the seek operation
                                return fileSystem.seek(ftEnt, find[0], find[1]);
                            } else {
                                // If the file table entry doesn't exist, return error
                                return ERROR;
                            }
                        } else {
                            // If no thread control block exists, return error
                            return ERROR;
                        }

                    case FORMAT:
                        // Perform disk formatting operation
                        {
                            // Format the disk with the specified parameter and return success or failure
                            return fileSystem.format(param) ? OK : ERROR;
                        }

                    case DELETE:
                        // Perform file deletion operation
                        {
                            // Delete the specified file and return success or failure
                            return fileSystem.delete((String) args) ? OK : ERROR;
                        }
                }
                // If none of the specified operations match the opCode, return error
                return ERROR;

            case INTERRUPT_DISK: // Disk interrupts
                // wake up the thread waiting for a service completion
                ioQueue.dequeueAndWakeup(COND_DISK_FIN);

                // wake up the thread waiting for a request acceptance
                //    ioQueue.dequeueAndWakeup(COND_DISK_REQ);

                return OK;
            case INTERRUPT_IO: // other I/O interrupts (not implemented)
                return OK;
        }
        return OK;
    }

    // Spawning a new thread
    private static int sysExec(String args[]) {
        String thrName = args[0]; // args[0] has a thread name
        Object thrObj = null;

        try {
            //get the user thread class from its name
            Class thrClass = Class.forName(thrName);
            if (args.length == 1) // no arguments
                thrObj = thrClass.newInstance(); // instantiate this class obj
            else { // some arguments
                // copy all arguments into thrArgs[] and make a new constructor
                // argument object from thrArgs[]
                String thrArgs[] = new String[args.length - 1];
                for (int i = 1; i < args.length; i++)
                    thrArgs[i - 1] = args[i];
                Object[] constructorArgs = new Object[] {
                    thrArgs
                };

                // locate this class object's constructors
                Constructor thrConst
                    = thrClass.getConstructor(new Class[] {
                        String[].class
                    });

                // instantiate this class object by calling this constructor
                // with arguments
                thrObj = thrConst.newInstance(constructorArgs);
            }
            // instantiate a new thread of this object
            Thread t = new Thread((Runnable) thrObj);

            // add this thread into scheduler's circular list.
            TCB newTcb = scheduler.addThread(t);
            return (newTcb != null) ? newTcb.getTid() : ERROR;
        } catch (ClassNotFoundException e) {
            System.out.println(e);
            return ERROR;
        } catch (NoSuchMethodException e) {
            System.out.println(e);
            return ERROR;
        } catch (InstantiationException e) {
            System.out.println(e);
            return ERROR;
        } catch (IllegalAccessException e) {
            System.out.println(e);
            return ERROR;
        } catch (InvocationTargetException e) {
            System.out.println(e);
            return ERROR;
        }
    }
}