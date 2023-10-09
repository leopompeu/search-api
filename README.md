# Java Webcrawler and term searcher
I've created this API to make the information gathering so much easier for every pentester.
It's a simple but useful tool, all you have to do is start this api in your docker with an environment variable "BASE_URL" to the web site which you want to crawl, after initializating, you can send your keyword for this API, all you have to do is send to your localhost:4567 a post notification with only one parameter in JSON: 'keyword'. This parameter will be the word that you will search in the selected website. The term needs to have at least 4 characters and 32 character maximum.
Then the API will return an ID that will have the result of your search. To see the results in real time, you have to send a GET requisition to /crawl/{the provided ID}, and the API will show to you in real time.
You can do multiple searchs at the same time, but if you stops the application you will lose your searches.

# Docker command
dockr build . -t searchapi

docker run -e BASE_URL=https://yourwebsite.com/ -p 4567:4567 --rm searchapi
