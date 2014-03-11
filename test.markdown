---
layout: default
title: TC359 - Lab1
type: lab
---


Test this thing:  http://cognosis.cas.msu.edu:8888/your-user-name/lab1.git
## Using Grails and Git##

A critical, but time-consuming step in writing any code is setting up an environment within which you can work comfortably.  The following guide will help you get started with that.

### Setting up an account ###

I maintain a server that you may use for running and testing applications.  You will likely be doing most of your codework on a lab computer or your own machine, but you will use the class server for testing your application.

#### Mac OS X ####

Open the Terminal application, under Applications -> Utilities -> Terminal.

Connect to the server using the ```ssh``` command:

~~~ bash
$ ssh your-msu-netid@cognosis.cas.msu.edu
~~~

Replace ```your-msu-netid``` with your own username (the same as your MSU Net ID). Enter your password (which you should have received via email) and you will be connected to the server.

Once you have connected, it should print out a bunch of information that you can ignore and give you a prompt that ends in a dollar sign. This is the command line on the server. 

#### Windows ####

You will need a secure shell (SSH) client to connect to the class server. I suggest you use Putty, which is available in the labs under Start -> Program Files -> Putty -> Putty. You can download it for free here: <http://www.chiark.greenend.org.uk/~sgtatham/putty/download.html>

1. In the dialog that opens up when you run it, enter the name of the server: ```cognosis.cas.msu.edu```
2. Click connect
-- If you see a security warning, that is OK. Select yes.
3. Enter your MSU Net ID, which is also your username on the class server.
4. Enter your password (which you should have received via email).

Once you have connected, the server will print out a bunch of information that you can ignore and give you a prompt that ends in a dollar sign. This is the command line on the server. 

### Change your password! ###

The __very first thing__ you should do once logging in is to change your password!  This is very important; please choose a good (secure) password that you won't forget.  To change your password, simply execute the ```passwd``` command and follow the prompts.

~~~ bash
$ passwd
~~~

### Create your first grails application ###

Let's jump right in to Grails!  Create a ```dev``` directory to hold your code, and then move into that directory.

~~~ bash
$ mkdir dev
$ cd dev
~~~

Now, create your first Grails application. 

~~~ bash
$ grails create-app lab1
~~~

This may take a few moments (it's a very old machine).  While you're waiting go check out the Grails [online tutorial](http://grails.org/doc/latest/guide/gettingStarted.html#creatingAnApplication).

Ok, if all went well you should be able to type ```ls``` and see a ```lab1``` directory.  Switch to this directory (```cd lab1```) and type ```ls``` again.  You will see a bunch of files and directories.  Feel free to poke around a bit.  (Note that you can move back up a directory by typing ```cd ..```).

### Run the application ###
Once you've satisfied your curiousity, let's make sure we're back in the ```lab1``` directory (you can verify what directory you're in by typing ```pwd```) and let's launch your new application.

~~~ bash
$ cd ~/dev/lab1
$ grails
~~~

Once again, there may be some thumb twiddling here. But if all goes well, you should see a nice friendly ```grails>``` prompt.  Hit the tab key to see a list of available commands.

Note that ```run-app``` is a command, and that is what we will do.  However, because we are on a shared machine, you will need to find a unique port that the server can use. You can pretty much choose anything within the range 1024 to 49151.  To start with, try the last 4 digits of your PID.

~~~ grails
grails> -Dserver.port=XXXX run-app
~~~

(Make sure to replace the XXXX with the last four digits of your PID). You should see some output while your files are compiled, and after what may seem like an eternity:

~~~ grails
| Server running. Browse to http://localhost:7770/lab1
| Application loaded in interactive mode. Type 'stop-app' to shutdown.
| Enter a script name to run. Use TAB for completion: 
grails>
~~~ 

Note, if you see output like:

~~~ grails
| Error Server failed to start for port 8888: Address already in use (Use --stacktrace to see the full trace)
| Error Error running script -Dserver.port=8888 run-app: org.codehaus.groovy.grails.cli.ScriptExitException (Use --stacktrace to see the full trace)
~~~

you will need to try to new port ID.  Try adding one to the last four of your PID until it works.

Once things are up and running open a browser and browse on over to ```http://cognosis.cas.msu.edu:XXXX/lab1``` (once again replacing XXXX with your unique port).  If all went well, you should see your very first grails application. Congratulations!

Make sure to shut down the application with ```stop-app``` and then exit grails by typing ```exit```.  Now it is time to make your code easy to access from other machines.

### Committing your code to the repository ###

We will be using a local instance of Gitlab to host our assignments, in order to avoid any issues with privacy on more public hosting service like Github. You should have received an email with your login information - if you have not seen it, __check your spam folder__ as it is very likely that the login message was snapped up. Please go ahead and sign into your account now if you have not already done so.

Go ahead and create a new repository named lab1 using the gitlab web interface.  Do not make the repository public. 

Now we're ready to push our code to this repository. Before committing any files, we'd like to make sure git ignores all of the compiled code, so we will create a ```.gitignore``` file in the project directory.

Make sure that you are once again in the ```lab1``` directory. From the bash prompt, execute the following:

~~~ bash
$ echo target > .gitignore
~~~

This creates a single line file with the word "target" inside, which tells git to ignore any files or directories with the word target in it. Files starting with a ```.``` are hidden by default, but you can verify that it is there by typing ```ls -l``` at the bash prompt.

Next, we need to add all of our changes â€“ which were entirely written for us by Grails â€“ to the git repository. This involves 3 steps: staging our changes, commiting them to the local repository, and pushing the changes to the main repository. But before we do that, we need to tell git who we are:

~~~ bash
$ git config --global user.name "your-user-name"
$ git config --global user.email "your-msu-email"
~~~

Make sure to replace your-user-name and your-msu-email with the correct values (but leave the quotes).  This setting is global, and you won't need to do this on cognosis again.

We also need to tell git that this directory is a repository:

~~~ bash
$ git init
~~~

This initializes the project as a repository.  You need to do this just once for every new project.

Now we can add our changes.  Use the command ```git add .``` to add all files that are not ignored according to ```.gitignore```. We can also specify individual files to add, but that would be time consuming here:

~~~ bash
$ git add .
~~~

Next, we need to commit these changes to our local version of the repository. No one else will be able to see these changes, but we can still access them:

~~~ bash
$ git commit -m 'My first commit'
~~~

Finally, if we want to make all of our changes available to others who have permission, we can push the changes to the main central repository.  First, we need to tell git where the repo lives.

~~~ bash
$ git remote add origin http://cognosis.cas.msu.edu:8888/your-user-name/lab1.git
~~~

Make sure to replace your-user-name with your actual username.  Finally, push the changes:

~~~ bash
$ git push origin master
~~~

(Note, because this is our first push, we have to specify where to push it to: origin master. Future pushes donâ€™t need this)

Because we are using http, rather than ssh to authenticate to the server, you will need to enter your gitlab login credentials.  These are just the username attached to your account (not your email address) and the password you set up in gitlab.

That's it!  You can verify that you have successfully pushed your code by browsing to the project on gitlab (```http://cognosis.cas.msu.edu:8888/your-user-name/lab1```).

### Pull your code to another machine ###

There are several good reasons to use a source code repository - it can save you from yourself, help you manage the complexity of creating production code, and enable effective collaboration. It will also afford you the opportunity to work on your code from any machine that has access to the internet, and that will be important.

#### Setting up a local machine ####

Ultimately, you will probably want to be working from a more convenient place than the lab. After all, telecommuting is one of the more compelling reasons for engaging in a programming career :-). In this section, I assume you do not have any of the following set up on your machine.  If you do, pat yourself on the back and skip to the next subsection.

1.  If you are running windows, install cygwin (<http://www.cygwin.com/>). This will give you a unix like environment and will make your life easier. After installing cygwin, make sure that you have [curl installed](http://stackoverflow.com/questions/3647569/how-do-i-install-curl-on-cygwin). 
1.  Check to see if you have git installed by opening up a terminal (native on the Mac, or using cygwin under Windows) and typing the word ```git --version``` at the prompt.  If you don't have git, you can download and install it from: <http://git-scm.com/downloads>.  You can find additional info on setting up git at <https://help.github.com/articles/set-up-git>.
1.  Install Java (<http://java.com/en>).
2.  Install gvm (<http://gvmtool.net/>). Gvm makes it easy to install Groovy and Grails.
1.  Once gvm is installed, install grails using ```gvm install grails```.

#### Getting your code ####
Ok, you are now ready to pull the code you just wrote down to your machine. Create a directory that will be dedicated to development, and the from within a terminal, switch to this directory and execute:

~~~ bash
git clone http://cognosis.cas.msu.edu:8888/your-user-name/lab1.git
~~~

You should now have a copy of your source files on your local machine.  You should also be able to run grails and test your application locally.  If you make any changes, and you want them to go into the repository, simply follow the procedure you used previously: ```git add```,```git commit```, and ```git push```.

Congrats, and nice job!
 





  
