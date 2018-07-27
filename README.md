# LunAdmin

LunAdmin is a web-service meant to make easier the management of administrative tasks such as paying employees or scheduling meetings. It is destined to become a slack bot which will send automatically messages to people about the tasks planned for them. It will be used first and mostly by the office manager but each employee will have to log-in at some point to fill personal informations and provide administrative papers such as insurance certificate.

## Motivations

LunAdmin comes from the need to centralize various tasks that were dispersed across multiple tools. It is first a web-service to gather every tasks and then it will be plug to slack to make a talking bot which will remind people about their duties. Every employee will be able to log-in to see what task they’re assigned to.

## What techs do we use.

LunAdmin is based on the play framework and uses Mongodb to store and retrieve data via ReactivMongo. \
It is using Http Requests to communicate. 

## What does it do

With LunAdmin you can schedule tasks that will send a message on slack to warn the people that are concerned \
You can manage users and tasks as well (delete/add/update) \
Updating a date will update the scheduled messages 

## Running the project

This github only contains the source code. \
First you need a mongo database which reference should be in application.conf under the label "mongodb.uri" \
In that database there should be 4 collections : \
-user \
-userGroup \
-task \
-taskCategory

Once you have your database then you just need to launch the application.

## Getting Started

A special test account is created on launch of the service to test the Admin part\
mail : LunAdmin@gmail.com \
pass : admin

For the user part with low rights, just register on the Home page

## Scheduling a task

Once your logged in on an user account all you need to do is go on the "Task" tab and click on the plus button.

There are 2 types of tasks : Unique and Recurrent(daily,weekly,monthly or yearly) \
\
Unique tasks are scheduled using Akka-actorSystem to delayed the send of a message based on the current time and the Start Date of the task \
\
Recurrent tasks are scheduled using Akka-Quartz-Scheduler which used cron expression to schedule a task based on a specific time ( ex : each monday at 12 AM)

Make sure the email of the user you are assigning the task to is valid on slack. \
Be careful with the extension of the email (.com/.fr) 

## Author

Amaury Cahuet - Lunatech FR - June/July 2018