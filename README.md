# hmpps-template-kotlin

This is a skeleton project from which to create new kotlin projects from.

# Instructions

If this is a Digital Prison Services project then the project will be created as part of bootstrapping - 
see https://github.com/ministryofjustice/dps-project-bootstrap.

## Renaming from HMPPS Template Kotlin

The `rename-project.bash` script takes a single argument - the name of the project and calculates from it:
* The main class name (project name converted to pascal case) 
* The project description (class name with spaces between the words)
* The main package name (project name with hyphens removed)

It then performs a search and replace and directory renames so the project is ready to be used.
