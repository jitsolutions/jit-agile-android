package pl.jitsolutions.agile.domain

data class Project(
        val name: String,
        val members: List<User>
)