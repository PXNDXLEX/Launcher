@Composable
fun CarCustomizationSheet(onIconSelected: (Int) -> Unit) {
    val icons = listOf(
        R.drawable.sedan_3d_blue, 
        R.drawable.truck_3d_red, 
        R.drawable.bike_3d_black
    )
    
    LazyRow {
        items(icons) { icon ->
            Image(
                painter = painterResource(id = icon),
                modifier = Modifier
                    .size(80.dp)
                    .clickable { onIconSelected(icon) }
            )
        }
    }
}