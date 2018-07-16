# LunAdmin

LunAdmin is a web-service meant to make easier the management of administrative tasks such as paying employees or scheduling meetings. It is destined to become a slack bot which will send automatically messages to people about the tasks planned for them. It will be used first and mostly by the office manager but each employee will have to log-in at some point to fill personal informations and provide administrative papers such as insurance certificate.

## Motivations

LunAdmin comes from the need to centralize various tasks that were dispersed across multiple tools. It is first a web-service to gather every tasks and then it will be plug to slack to make a talking bot which will remind people about their duties. Every employee will be able to log-in to see what task they’re assigned to.

## What techs do we use.

LunAdmin is based on the play framework and use Mongodb to store and retrieve data via ReactivMongo. It is using Http Requests to communicate. 

## Implemented Features

Listing/update/delete/add of Task / Users   ( Admin Mode Only )

Since registering is not implemented yet : \
A special test account is created on launch of the service \
mail : LunAdmin@gmail.com \
pass : admin

## Author

Amaury Cahuet - Lunatech FR - June/July 2018