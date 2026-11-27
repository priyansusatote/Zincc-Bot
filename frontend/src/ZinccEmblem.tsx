export function ZinccEmblem() {
	return (
		<div className="orb" aria-hidden="true">
			{Array.from({ length: 22 }, (_, i) => (
				<i
					key={i.toString()}
					style={{ transform: `rotate(${i * 8.2}deg) rotateY(55deg)` }}
				/>
			))}
		</div>
	);
}
