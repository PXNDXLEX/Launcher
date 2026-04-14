@Composable
fun MapWidgetPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Navegación Activa",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(Icons.Default.Place, contentDescription = "GPS", tint = Color(0xFF4CAF50))
        }

        // Simulación del renderizado 3D de tu vehículo
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = "Car",
                tint = Color.LightGray,
                modifier = Modifier.size(100.dp)
            )
            Text(
                text = "Renderizando: Kia Rio Stylus (2009)",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        Text(
            text = "Próximo giro en 300m",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}