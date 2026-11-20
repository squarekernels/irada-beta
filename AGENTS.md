**One-sentence project description**

Irada is a native-first iOS and Android app that turns natural-language brain dumps into user-reviewed, locally stored task plans and reminders.

**Android:**

- **Package/dependency management:** Gradle, Google Maven, Maven Central
- **Build:** Gradle Wrapper, Android Gradle Plugin (AGP), Kotlin Gradle Plugin
- **Type checking:** Kotlin K2 compiler, integrated into Gradle compilation
- **Static analysis:** Android Lint
- **Optional code-quality tooling:** Detekt, ktlint

**iOS:** [will update later]

## Git Workflow Instructions

### Before Starting Work

1. Create a new feature branch and switch to it:
   ```bash
   git switch -c <feature-branch-name>
   ```

2. Continue performing the assigned task on the new branch.
3. Do not make changes directly to main or master.
After Completing Work
1. Complete the task and verify that all changes work as expected.
2. Summarize the changes made.
3. Request explicit approval from the repository owner before committing or pushing.
4. Only after receiving approval, run the following commands:
   git add .
   git commit -m "<descriptive-commit-message>"
   git push -u origin HEAD

### After a Pull Request Merges

Once you verify that the feature branch’s pull request has merged into `main`, delete the feature branch from `origin` and locally. If the branch has new commits since the merge, preserve it until those commits are accounted for.

**Important Rules**
- Never commit or push without explicit owner approval.
- Always work on a separate feature branch.
- Use clear, descriptive branch names and commit messages.
- Push to the existing origin remote.
- Never push directly to main or master.
- Never force-push unless explicitly authorized.

## Co-Authored-By

Every commit created by Codex for this project must end with the following co-author trailer, including commits pushed to GitHub. Separate the trailer from the title or optional body with a blank line.

```text
[The commit title]

[Optional commit body]

Co-Authored-By: codex <codex@openai.com>
```


