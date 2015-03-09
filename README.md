# mturk-screener
A workflow for setting up Amazon Mechanical Turk with a screener survey to grant a qualification for a full survey.

This script assumes surveys were created with [surveygizmo](http://surveygizmo.com/), allowing you to easily download the csv file for the results.

The java library for Amazon Mechanical Turk uses ant, which I find highly annoying. But I didn't want to go through the hassle of figuring out another way, so I just put up with it.

There are four basic commands, that should roughly be run in order:

1. `ant screener` creates the screener question, as defined by `code/screener/screenerquestion.xml`.
2. `ant full` creates the full survey, as defined by `code/full/fullsurveyquestion.xml`.
3. `ant qualifier` reviews the responses to the screener question, pays respondents, and qualifies people for the full survey.
4. `ant responses` reviews the responses to the full survey and pays accordingly.

Survey responses to both the screener and the full survey need to be generated from exports from surveygizmo, replacing the `gizmo.csv` files in both `code/screener/` and `code/full/`.