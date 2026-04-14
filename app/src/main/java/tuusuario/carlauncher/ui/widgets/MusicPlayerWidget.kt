@Composable
fun MusicPlayerWidget() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = "Música", tint = Color(0xFF03A9F4))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reproduciendo ahora", color = Color.Gray, fontSize = 14.sp)
        }

        Column {
            Text(
                text = "Mix Especial para Amola", 
                color = Color.White, 
                fontSize = 22.sp, 
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text("Spotify", color = Color(0xFF1DB954), fontSize = 16.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Acción anterior */ }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { /* Acción Play/Pause */ }) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(56.dp))
            }
            IconButton(onClick = { /* Acción siguiente */ }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}