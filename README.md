# hmpps-template-kotlin

This is a skeleton project from which to create new kotlin projects from.

# Instructions

If this is a Digital Prison Services project then the project will be created as part of bootstrapping - 
see https://github.com/ministryofjustice/dps-project-bootstrap.

## Renaming from HMPPS Template Kotlin - github Actions

Once the new repository is deployed. Navigate to the repository in github, and select the `Actions` tab.
Click the link to `Enable Actions on this repository`.

Find the Action workflow named: `rename-project-create-pr` and click `Run workflow`.  This workflow will will
execute the `rename-project.bash` and create Pull Request for you to review.  Review the PR and merge.

Note: ideally this workflow would run automatically however due to a recent change github Actions are not
enabled by default on newly created repos. There is no way to enable Actions other then to click the button in the UI.
If this situation changes we will update this project so that the workflow is triggered during the bootstrap project.
Further reading: <https://github.community/t/workflow-isnt-enabled-in-repos-generated-from-template/136421>

## Manually renaming from HMPPS Template Kotlin

Run the `rename-project.bash` and create a PR.

The `rename-project.bash` script takes a single argument - the name of the project and calculates from it:
* The main class name (project name converted to pascal case) 
* The project description (class name with spaces between the words)
* The main package name (project name with hyphens removed)

It then performs a search and replace and directory renames so the project is ready to be used.
